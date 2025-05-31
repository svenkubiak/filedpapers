const express = require('express');
const axios = require('axios');
const cheerio = require('cheerio');
const sharp = require('sharp');
const { URL } = require('url');

const app = express();
const PORT = 3000;

// Constants
const MAX_IMAGE_SIZE = 1024;
const MAX_IMAGE_AREA = MAX_IMAGE_SIZE * MAX_IMAGE_SIZE; // 1MB in pixels
const MAX_IMAGE_CANDIDATES = 5;

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
      maxContentLength: 10 * 1024 * 1024
    });
    
    const metadata = await sharp(response.data).metadata();
    const area = metadata.width * metadata.height;
    if (area <= MAX_IMAGE_AREA) {
      return true;
    }
    return false;
  } catch (e) {
    return false;
  }
};

// Extract title following the specified rules
const extractTitle = ($) => {
  // Open Graph title
  const ogTitle = $('meta[property="og:title"]').attr('content');
  if (ogTitle) return cleanText(ogTitle);

  // Twitter Card title
  const twitterTitle = $('meta[name="twitter:title"]').attr('content');
  if (twitterTitle) return cleanText(twitterTitle);

  // Meta title
  const metaTitle = $('meta[name="title"]').attr('content');
  if (metaTitle) return cleanText(metaTitle);

  // Document title
  const docTitle = $('title').text();
  if (docTitle) return cleanText(docTitle);

  // H1 tag
  const h1Title = $('h1').first().text();
  if (h1Title) return cleanText(h1Title);

  // H2 tag
  const h2Title = $('h2').first().text();
  if (h2Title) return cleanText(h2Title);

  // Try to find any heading that might contain the title
  const anyHeading = $('h1, h2, h3, h4, h5, h6').first().text();
  if (anyHeading) return cleanText(anyHeading);

  // If we still don't have a title, try to find the first non-empty text node
  const firstText = $('body').clone().children().remove().end().text();
  if (firstText) return cleanText(firstText);

  return null;
};

// Extract image following the specified rules
const extractImage = async ($, baseUrl) => {
  // Open Graph image
  const ogImage = $('meta[property="og:image"]').attr('content');
  if (ogImage) {
    const resolvedUrl = resolveUrl(ogImage, baseUrl);
    if (resolvedUrl && await getImageDimensions(resolvedUrl)) {
      return resolvedUrl;
    }
  }

  // Twitter Card image
  const twitterImage = $('meta[name="twitter:image"]').attr('content');
  if (twitterImage) {
    const resolvedUrl = resolveUrl(twitterImage, baseUrl);
    if (resolvedUrl && await getImageDimensions(resolvedUrl)) {
      return resolvedUrl;
    }
  }

  // Image_src link
  const imageSrc = $('link[rel="image_src"]').attr('href');
  if (imageSrc) {
    const resolvedUrl = resolveUrl(imageSrc, baseUrl);
    if (resolvedUrl && await getImageDimensions(resolvedUrl)) {
      return resolvedUrl;
    }
  }

  // Best image from document body (limited to 5 candidates)
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

// Extract description following the specified rules
const extractDescription = ($) => {
  // Open Graph description
  const ogDesc = $('meta[property="og:description"]').attr('content');
  if (ogDesc) return cleanText(ogDesc);

  // Twitter Card description
  const twitterDesc = $('meta[name="twitter:description"]').attr('content');
  if (twitterDesc) return cleanText(twitterDesc);

  // Meta description
  const metaDesc = $('meta[name="description"]').attr('content');
  if (metaDesc) return cleanText(metaDesc);

  // First visible paragraph
  const firstParagraph = $('p').first().text();
  if (firstParagraph) return cleanText(firstParagraph);

  return null;
};

// Main endpoint
app.get('/preview', async (req, res) => {
  const url = req.query.url;
  const lang = req.query.lang || 'en-US';
  if (!url) return res.status(400).json({ error: 'Missing ?url=' });

  try {
    let axiosResponse;
    const urlObj = new URL(url);
    const domain = urlObj.hostname;

    // Select User-Agents based on domain
    let userAgents;
    if (domain.includes('youtube.com')) {
      userAgents = [
        'Mozilla/5.0 (compatible; Googlebot/2.1; +http://www.google.com/bot.html)',
        'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36'
      ];
    } else if (domain.includes('google.com')) {
      userAgents = [
        'Mozilla/5.0 (compatible; LinkPreviewBot/1.0; +http://example.com/bot)',
        'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36'
      ];
    } else {
      // Default User-Agents for other sites
      userAgents = [
        'Mozilla/5.0 (compatible; LinkPreviewBot/1.0; +http://example.com/bot)',
        'Mozilla/5.0 (compatible; Googlebot/2.1; +http://www.google.com/bot.html)',
        'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36'
      ];
    }

    let lastError;
    for (const userAgent of userAgents) {
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
    
    const metadataResponse = {
      title: extractTitle($),
      description: extractDescription($),
      image: await extractImage($, url),
      domain: extractDomain($, url)
    };

    res.json(metadataResponse);
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

app.listen(PORT, () => {
  console.log(`Filedpapers-Metascraper running on http://localhost:${PORT}`);
});
