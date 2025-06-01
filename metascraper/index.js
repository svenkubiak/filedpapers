const express = require('express');
const axios = require('axios');
const sharp = require('sharp');
const { URL } = require('url');
const metascraper = require('metascraper');
const metascraperTitle = require('metascraper-title');
const metascraperDescription = require('metascraper-description');
const metascraperImage = require('metascraper-image');
const metascraperUrl = require('metascraper-url');
const cheerio = require('cheerio');

const app = express();
const PORT = 3000;

const MAX_IMAGE_SIZE = 1024;
const MAX_IMAGE_AREA = MAX_IMAGE_SIZE * MAX_IMAGE_SIZE; // 1MB in pixels
const MAX_IMAGE_CANDIDATES = 5;

const USER_AGENTS = [
  'Mozilla/5.0 (compatible; LinkPreviewBot/1.0; +http://example.com/bot)',
  'Mozilla/5.0 (compatible; Googlebot/2.1; +http://www.google.com/bot.html)',
  'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36'
];

const scraper = metascraper([
  metascraperTitle(),
  metascraperDescription(),
  metascraperImage(),
  metascraperUrl()
]);

// Helper function to clean text
const cleanText = (text) => {
  return text ? text.trim().replace(/\s+/g, ' ') : null;
};

// Helper function to resolve URLs
const resolveUrl = (url, baseUrl) => {
  try {
    if (!url || url.startsWith('data:')) return null;
    const baseUrlWithoutFragment = baseUrl.split('#')[0];
    const resolvedUrl = new URL(url, baseUrlWithoutFragment);
    return resolvedUrl.href;
  } catch (e) {
    return null;
  }
};

// Helper function to check image dimensions
const getImageDimensions = async (imageUrl) => {
  try {
    const response = await axios.get(imageUrl, {
      responseType: 'arraybuffer',
      timeout: 5000,
      maxContentLength: 10 * 1024 * 1024,
      headers: {
        'User-Agent': 'Mozilla/5.0 (compatible; LinkPreviewBot/1.0; +http://example.com/bot)',
        'Accept': 'image/webp,image/apng,image/*,*/*;q=0.8',
        'Accept-Encoding': 'gzip, deflate, br',
        'Connection': 'keep-alive'
      }
    });
    
    const metadata = await sharp(response.data).metadata();
    const area = metadata.width * metadata.height;

    return area <= MAX_IMAGE_AREA;
  } catch (e) {
    console.error('Error checking image dimensions:', e.message);
    return false;
  }
};

// Extract domain following the specified rules
const extractDomain = ($, originalUrl) => {
  try {
    // Canonical link
    const canonicalUrl = $('link[rel="canonical"]').attr('href');
    if (canonicalUrl) {
      const domain = new URL(canonicalUrl).hostname;
      return domain.startsWith('www.') ? domain.substring(4) : domain;
    }

    // Open Graph URL
    const ogUrl = $('meta[property="og:url"]').attr('content');
    if (ogUrl) {
      const domain = new URL(ogUrl).hostname;
      return domain.startsWith('www.') ? domain.substring(4) : domain;
    }

    // Original URL
    const domain = new URL(originalUrl).hostname;
    return domain.startsWith('www.') ? domain.substring(4) : domain;
  } catch (e) {
    return null;
  }
};

// Extract image following the specified rules
const extractImage = async ($, baseUrl) => {
  // Try metascraper's image first
  const metascraperImage = $('meta[property="og:image"]').attr('content');
  if (metascraperImage) {
    const resolvedUrl = resolveUrl(metascraperImage, baseUrl);
    if (resolvedUrl && await getImageDimensions(resolvedUrl)) {
      return resolvedUrl;
    }
  }

  // Try Twitter Card image
  const twitterImage = $('meta[name="twitter:image"]').attr('content');
  if (twitterImage) {
    const resolvedUrl = resolveUrl(twitterImage, baseUrl);
    if (resolvedUrl && await getImageDimensions(resolvedUrl)) {
      return resolvedUrl;
    }
  }

  // Try image_src link
  const imageSrc = $('link[rel="image_src"]').attr('href');
  if (imageSrc) {
    const resolvedUrl = resolveUrl(imageSrc, baseUrl);
    if (resolvedUrl && await getImageDimensions(resolvedUrl)) {
      return resolvedUrl;
    }
  }

  // Try images from document body (limited to 5 candidates)
  let candidates = 0;
  for (const el of $('img').get()) {
    if (candidates >= MAX_IMAGE_CANDIDATES) break;
    
    const src = $(el).attr('src');
    if (!src || src.startsWith('data:')) continue;
    
    const resolvedUrl = resolveUrl(src, baseUrl);
    if (resolvedUrl && await getImageDimensions(resolvedUrl)) {
      return resolvedUrl;
    }
    candidates++;
  }

  return null;
};

// Main endpoint
app.get('/preview', async (req, res) => {
  const url = req.query.url;
  const lang = req.query.lang || 'en-US';
  if (!url) return res.status(400).json({ error: 'Missing ?url=' });

  try {
    let axiosResponse;
    let lastError;

    // Try each User-Agent until one succeeds
    for (const userAgent of USER_AGENTS) {
      try {
        axiosResponse = await axios.get(url, {
          headers: {
            'User-Agent': userAgent,
            'Accept': 'text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8',
            'Accept-Language': `${lang},en;q=0.8,*;q=0.5"`,
            'Accept-Encoding': 'gzip, deflate, br',
            'Connection': 'keep-alive'
          },
          timeout: 15000,
          maxRedirects: 5
        });
        break;
      } catch (error) {
        lastError = error;
      }
    }

    if (!axiosResponse) {
      throw lastError || new Error('All User-Agent attempts failed');
    }

    const html = axiosResponse.data;
    const $ = cheerio.load(html);
    
    // Use metascraper to get metadata
    const metadata = await scraper({ html, url });

    const metadataResponse = {
      title: cleanText(metadata.title),
      description: cleanText(metadata.description),
      image: await extractImage($, url),
      domain: extractDomain($, url)
    };

    res.json(metadataResponse);
  } catch (err) {
    console.error('Error processing request:', err);
    res.status(500).json({ error: err.message });
  }
});

app.listen(PORT, () => {
  console.log(`Filedpapers-Metascraper running on http://localhost:${PORT}`);
});
