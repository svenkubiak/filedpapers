const express = require('express');
const axios = require('axios');
const cheerio = require('cheerio');
const sharp = require('sharp');
const { URL } = require('url');
const { exec } = require('child_process');
const fs = require('fs').promises;
const path = require('path');
const { promisify } = require('util');

const execAsync = promisify(exec);

const app = express();
const PORT = 3000;

// Constants
const MAX_IMAGE_SIZE = 1024;
const MAX_IMAGE_AREA = MAX_IMAGE_SIZE * MAX_IMAGE_SIZE; // 1MB in pixels
const MIN_IMAGE_SIZE = 100;
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
    
    // Check if the format is one of the allowed formats
    const validFormats = ['png', 'jpeg', 'jpg', 'jp2', 'webp'];
    if (!validFormats.includes(metadata.format)) {
      return false;
    }
    
    const area = metadata.width * metadata.height;
    
    // Calculate aspect ratio (both width/height and height/width to handle both orientations)
    const aspectRatioWidth = metadata.width / metadata.height;
    const aspectRatioHeight = metadata.height / metadata.width;
    
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
      const fullUrl = new URL(canonicalUrl, originalUrl).href;
      const domain = new URL(fullUrl).hostname;
      return domain.startsWith('www.') ? domain.substring(4) : domain;
    }

    // Open Graph URL
    const ogUrl = $('meta[property="og:url"]').attr('content');
    if (ogUrl) {
      const fullUrl = new URL(ogUrl, originalUrl).href;
      const domain = new URL(fullUrl).hostname;
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

// Health check endpoint
app.get('/health', (req, res) => {
  res.json({ status: 'ok' });
});

// Main endpoint
app.get('/preview', async (req, res) => {
  const url = req.query.url;
  const lang = req.query.lang || 'en-US';
  if (!url) return res.status(400).json({ error: 'Missing ?url=' });

  try {
    let bestMetadata = {
      title: null,
      description: null,
      image: null,
      domain: null
    };

    // Try each User-Agent
    let htmlValidationFailed = true;
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
        
        // Check if the response is HTML
        const contentType = axiosResponse.headers['content-type'] || '';
        const isHtmlContentType = contentType.includes('text/html') || 
                                 contentType.includes('application/xhtml+xml');
        
        const html = axiosResponse.data;
        const isHtmlContent = typeof html === 'string' && 
                            (html.trim().toLowerCase().startsWith('<!doctype html') || 
                             html.trim().toLowerCase().startsWith('<html'));
        
        if (!isHtmlContentType || !isHtmlContent) {
          console.log(`Skipping non-HTML content from ${url} (Content-Type: ${contentType})`);
          continue; // Skip to next User-Agent
        }
        
        htmlValidationFailed = false;
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
        // Continue with next User-Agent
      }
    }

    // If HTML validation failed for all User-Agents, return an error response
    if (htmlValidationFailed) {
      return res.status(400).json({
        error: 'The provided URL did not return valid HTML content',
        metadata: {
          title: null,
          description: null,
          image: null,
          domain: null
        }
      });
    }

    // Always return the metadata, even if some fields are null
    res.json(bestMetadata);
  } catch (err) {
    // In case of any errors, return all null values with 200 status
    res.json({
      title: null,
      description: null,
      image: null,
      domain: null
    });
  }
});

// Archive endpoint
app.get('/archive', async (req, res) => {
  const url = req.query.url;
  if (!url) return res.status(400).json({ error: 'Missing ?url=' });

  // Set HTTP request timeout
  req.setTimeout(90000); // 90 seconds
  res.setTimeout(90000); // 90 seconds

  try {
    // Validate URL
    new URL(url);
    
    // Generate a unique temporary filename
    const timestamp = Date.now();
    const outputFile = path.join(require('os').tmpdir(), `archive_${timestamp}.html`);
    

    
    // Use SingleFile CLI with optimized options for speed and cookie banner blocking
    const singleFileCommand = `npx single-file "${url}" "${outputFile}" --browser-headless true --browser-executable-path /usr/bin/chromium --browser-wait-until load --browser-load-max-time 30000 --browser-capture-max-time 30000 --load-deferred-images false --remove-hidden-elements false --remove-unused-styles false --compress-html false --compress-css false --group-duplicate-images false --resolve-links false --insert-single-file-comment false --blocked-URL-pattern ".*cookie.*" --blocked-URL-pattern ".*consent.*" --blocked-URL-pattern ".*gdpr.*" --blocked-URL-pattern ".*privacy.*" --blocked-URL-pattern ".*banner.*" --blocked-URL-pattern ".*popup.*" --blocked-URL-pattern ".*modal.*" --blocked-URL-pattern ".*overlay.*"`;
    
    const { stdout, stderr } = await execAsync(singleFileCommand, {
      timeout: 60000, // 1 minute timeout
      maxBuffer: 50 * 1024 * 1024 // 50MB buffer
    });
    
    if (stderr && !stderr.includes('Warning')) {
      console.error('SingleFile stderr:', stderr);
    }
    
    // Read the generated file
    const archiveContent = await fs.readFile(outputFile, 'utf8');
    
    // Convert to base64
    const base64Content = Buffer.from(archiveContent, 'utf8').toString('base64');
    
    // Clean up the temporary file
    try {
      await fs.unlink(outputFile);
    } catch (cleanupError) {
      console.error('Failed to cleanup temporary file:', cleanupError);
    }
    
    // Return the base64 encoded archive
    res.json({
      success: true,
      archive: base64Content,
      timestamp: new Date().toISOString()
    });
    
  } catch (error) {
    console.error('Archive error:', error);
    

    
    res.status(500).json({
      success: false,
      error: 'Failed to archive the website',
      details: error.message
    });
  }
});



// Helper function to check if we have all required metadata
const hasAllRequiredMetadata = (metadata) => {
  return metadata.title && metadata.image && metadata.domain;
};

app.listen(PORT, () => {
  console.log(`Filedpapers-Metascraper up and running.`);
});
