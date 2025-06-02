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
const MIN_IMAGE_SIZE = 50;
const MIN_IMAGE_AREA = MIN_IMAGE_SIZE * MIN_IMAGE_SIZE; // 2.5KB in pixels
const MAX_IMAGE_CANDIDATES = 10;
const MAX_ASPECT_RATIO = 3; // Maximum width/height or height/width ratio

// Common set of User-Agents for all URLs
const USER_AGENTS = [
  'Mozilla/5.0 (compatible; Googlebot/2.1; +http://www.google.com/bot.html)',
  'Mozilla/5.0 (compatible; LinkPreviewBot/1.0; +http://example.com/bot)',
  'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36'
];

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
    
    // Calculate aspect ratio (both width/height and height/width to handle both orientations)
    const aspectRatioWidth = metadata.width / metadata.height;
    const aspectRatioHeight = metadata.height / metadata.width;
    
    // Check dimensions and aspect ratio
    return metadata.width >= MIN_IMAGE_SIZE &&
        metadata.height >= MIN_IMAGE_SIZE &&
        area >= MIN_IMAGE_AREA &&
        area <= MAX_IMAGE_AREA &&
        aspectRatioWidth <= MAX_ASPECT_RATIO &&
        aspectRatioHeight <= MAX_ASPECT_RATIO;

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
    let lastError;
    let bestMetadata = {
      title: null,
      description: null,
      image: null,
      domain: null
    };

    // Try each User-Agent
    for (const userAgent of USER_AGENTS) {
      try {
        const axiosResponse = await axios.get(url, {
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
        
        const html = axiosResponse.data;
        const $ = cheerio.load(html);
        
        // Try to get each field, only update if we don't have it yet or if it's better
        const currentTitle = extractTitle($);
        if (currentTitle && (!bestMetadata.title || currentTitle.length > bestMetadata.title.length)) {
          bestMetadata.title = currentTitle;
        }

        const currentDescription = extractDescription($);
        if (currentDescription && (!bestMetadata.description || currentDescription.length > bestMetadata.description.length)) {
          bestMetadata.description = currentDescription;
        }

        const currentImage = await extractImage($, url);
        // Always update image if we find a better one (not the default Google Maps icon)
        if (currentImage && (!bestMetadata.image || 
            (currentImage.includes('googleusercontent.com') && !bestMetadata.image.includes('googleusercontent.com')))) {
          bestMetadata.image = currentImage;
        }

        const currentDomain = extractDomain($, url);
        if (currentDomain && !bestMetadata.domain) {
          bestMetadata.domain = currentDomain;
        }

        // If we have all required metadata, we can stop trying
        if (hasAllRequiredMetadata(bestMetadata)) {
          break;
        }
      } catch (error) {
        lastError = error;
      }
    }

    if (!hasAllRequiredMetadata(bestMetadata)) {
      throw lastError || new Error('Failed to get all required metadata');
    }

    res.json(bestMetadata);
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

// Helper function to check if we have all required metadata
const hasAllRequiredMetadata = (metadata) => {
  return metadata.title && metadata.image && metadata.domain;
};

app.listen(PORT, () => {
  console.log(`Filedpapers-Metascraper up and running.`);
});
