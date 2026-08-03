const express = require('express');
const axios = require('axios');
const cheerio = require('cheerio');
const sharp = require('sharp');
const puppeteer = require('puppeteer-core');
const { URL } = require('url');
const { exec, execFile } = require('child_process');
const fs = require('fs').promises;
const path = require('path');
const { promisify } = require('util');
const os = require('os');

const execAsync = promisify(exec);
const execFileAsync = promisify(execFile);
const { access } = require('fs').promises;

const app = express();
const PORT = 3000;

// Helper function to find Chromium executable
const findChromiumPath = async () => {
  // Try environment variable first
  if (process.env.CHROME_BIN) {
    try {
      await access(process.env.CHROME_BIN);
      return process.env.CHROME_BIN;
    } catch (e) {
      // Path doesn't exist, continue
    }
  }

  // Common Chromium paths to try
  const possiblePaths = [
    '/usr/bin/chromium',
    '/usr/bin/chromium-browser',
    '/usr/bin/google-chrome',
    '/usr/bin/google-chrome-stable',
    '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome',
    '/Applications/Chromium.app/Contents/MacOS/Chromium',
    process.platform === 'darwin' ? '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome' : null,
  ].filter(Boolean);

  for (const chromiumPath of possiblePaths) {
    try {
      await access(chromiumPath);
      return chromiumPath;
    } catch (e) {
      // Path doesn't exist, try next
      continue;
    }
  }

  // If not found, try to use 'which' command
  try {
    const { stdout } = await execAsync('which chromium || which chromium-browser || which google-chrome || which google-chrome-stable');
    const foundPath = stdout.trim();
    if (foundPath) {
      return foundPath;
    }
  } catch (e) {
    // which command failed, continue
  }

  // Last resort: return null
  return null;
};

const getChromiumArgs = () => {
  const flags = process.env.CHROMIUM_FLAGS || '--no-sandbox --disable-setuid-sandbox --disable-dev-shm-usage --disable-gpu';
  return flags.split(/\s+/).filter(Boolean);
};

// Constants
const MAX_IMAGE_SIZE = 1024;
const MAX_IMAGE_AREA = MAX_IMAGE_SIZE * MAX_IMAGE_SIZE; // 1MB in pixels
const MIN_IMAGE_SIZE = 100;
const MIN_IMAGE_AREA = MIN_IMAGE_SIZE * MIN_IMAGE_SIZE; // 2.5KB in pixels
const MAX_IMAGE_CANDIDATES = 10;
const MAX_ASPECT_RATIO = 3; // Maximum width/height or height/width ratio
const BROWSER_VIEWPORT = { width: 1280, height: 720 };
const BROWSER_TIMEOUT_MS = 20000;
const BROWSER_SETTLE_MS = 1000;
const SCREENSHOT_DIR = path.join(os.tmpdir(), 'metascraper-screenshots');
const SCREENSHOT_TTL_MS = 60 * 60 * 1000;
const SINGLE_FILE_BIN = path.join(__dirname, 'node_modules', '.bin', 'single-file');

const launchBrowser = async () => {
  const chromiumPath = await findChromiumPath();
  if (!chromiumPath) {
    return null;
  }

  return puppeteer.launch({
    executablePath: chromiumPath,
    headless: true,
    args: getChromiumArgs(),
    timeout: BROWSER_TIMEOUT_MS
  });
};

const withBrowserPage = async (url, pageHandler) => {
  let browser;

  try {
    browser = await launchBrowser();
    if (!browser) {
      console.error('Browser launch failed: Chromium not found');
      return null;
    }

    const page = await browser.newPage();
    await page.setViewport(BROWSER_VIEWPORT);
    await page.goto(url, {
      waitUntil: 'networkidle2',
      timeout: BROWSER_TIMEOUT_MS
    });
    await new Promise((resolve) => setTimeout(resolve, BROWSER_SETTLE_MS));

    return await pageHandler(page);
  } catch (error) {
    console.error('Browser page error:', error);
    return null;
  } finally {
    if (browser) {
      await browser.close().catch(() => {});
    }
  }
};

// Render Amazon pages with single-file-cli — Puppeteer page.content() does not reliably capture product metadata
const renderWithBrowser = async (url) => {
  try {
    const timestamp = Date.now();
    const outputFile = path.join(os.tmpdir(), `preview_${timestamp}.html`);
    const chromiumPath = await findChromiumPath();
    const chromiumFlags = getChromiumArgs();

    const args = [
      url,
      outputFile,
      '--browser-headless', 'true',
      '--browser-wait-until', 'load',
      '--browser-load-max-time', '10000',
      '--browser-capture-max-time', '10000',
      '--load-deferred-images', 'false',
      '--remove-hidden-elements', 'false',
      '--remove-unused-styles', 'false',
      '--compress-html', 'false',
      '--compress-css', 'false',
      '--group-duplicate-images', 'false',
      '--resolve-links', 'false',
      '--insert-single-file-comment', 'false',
      '--blocked-URL-pattern', '.*cookie.*',
      '--blocked-URL-pattern', '.*consent.*',
      '--blocked-URL-pattern', '.*gdpr.*',
      '--blocked-URL-pattern', '.*privacy.*',
      '--blocked-URL-pattern', '.*banner.*',
      '--blocked-URL-pattern', '.*popup.*',
      '--blocked-URL-pattern', '.*modal.*',
      '--blocked-URL-pattern', '.*overlay.*'
    ];

    if (chromiumPath) {
      args.push('--browser-executable-path', chromiumPath);
    } else {
      console.log('Chromium not found in standard paths, letting single-file auto-detect');
    }

    // single-file-cli expects browser-args as a JSON array
    if (chromiumFlags.length) {
      args.push('--browser-args', JSON.stringify(chromiumFlags));
    }

    const { stderr } = await execFileAsync(SINGLE_FILE_BIN, args, {
      timeout: 15000,
      maxBuffer: 50 * 1024 * 1024
    });

    if (stderr && !stderr.includes('Warning')) {
      console.error('SingleFile stderr:', stderr);
    }

    const html = await fs.readFile(outputFile, 'utf8');

    try {
      await fs.unlink(outputFile);
    } catch (cleanupError) {
      console.error('Failed to cleanup temporary file:', cleanupError);
    }

    return html;
  } catch (error) {
    console.error('Browser rendering error:', error);
    return null;
  }
};

const extractDomainFromUrl = (url) => {
  try {
    const domain = new URL(url).hostname;
    return domain.startsWith('www.') ? domain.substring(4) : domain;
  } catch (e) {
    return null;
  }
};

const BOT_CHALLENGE_INDICATORS = [
  'cf-browser-verification',
  'challenge-platform',
  'cdn-cgi/challenge',
  'checking your browser before accessing',
  'checking your browser',
  'just a moment',
  'attention required',
  'cloudflare ray id',
  'enable javascript and cookies',
  '__cf_chl',
  'turnstile',
  'ddos protection by cloudflare',
  'please wait while we verify',
  'bot verification'
];

const BOT_CHALLENGE_TITLES = [
  'just a moment',
  'attention required',
  'please wait',
  'checking your browser',
  'access denied',
  '403 forbidden',
  'security check'
];

const isBotChallengePage = (html, title) => {
  const lowerHtml = (html || '').toLowerCase();
  const lowerTitle = (title || '').toLowerCase();

  if (BOT_CHALLENGE_TITLES.some((entry) => lowerTitle.includes(entry))) {
    return true;
  }

  return BOT_CHALLENGE_INDICATORS.some((indicator) => lowerHtml.includes(indicator));
};

const stripBotChallengeMetadata = (metadata) => {
  const title = (metadata.title || '').toLowerCase();
  if (BOT_CHALLENGE_TITLES.some((entry) => title.includes(entry))) {
    metadata.title = null;
    metadata.description = null;
  }
  return metadata;
};

const ensureScreenshotDir = async () => {
  await fs.mkdir(SCREENSHOT_DIR, { recursive: true });
};

const cleanupOldScreenshots = async () => {
  try {
    const files = await fs.readdir(SCREENSHOT_DIR);
    const now = Date.now();

    await Promise.all(files.map(async (file) => {
      const filePath = path.join(SCREENSHOT_DIR, file);
      try {
        const stat = await fs.stat(filePath);
        if (now - stat.mtimeMs > SCREENSHOT_TTL_MS) {
          await fs.unlink(filePath);
        }
      } catch (e) {
        // Ignore missing or unreadable files
      }
    }));
  } catch (e) {
    // Ignore cleanup errors
  }
};

const saveScreenshotFromPage = async (page) => {
  const html = await page.content();
  const title = await page.title();
  if (isBotChallengePage(html, title)) {
    console.log('Skipping screenshot of bot challenge page');
    return null;
  }

  await ensureScreenshotDir();
  await cleanupOldScreenshots();

  const pngBuffer = await page.screenshot({ type: 'png' });
  const filename = `${Date.now()}_${Math.random().toString(36).slice(2, 10)}.webp`;
  const filePath = path.join(SCREENSHOT_DIR, filename);

  const webpBuffer = await sharp(pngBuffer)
    .resize(MAX_IMAGE_SIZE, MAX_IMAGE_SIZE, { fit: 'inside', withoutEnlargement: true })
    .webp({ quality: 80 })
    .toBuffer();

  await fs.writeFile(filePath, webpBuffer);

  return `/screenshots/${filename}`;
};

const captureScreenshot = async (url) => {
  return withBrowserPage(url, async (page) => saveScreenshotFromPage(page));
};

const fetchMetadataWithBrowser = async (url) => {
  return withBrowserPage(url, async (page) => {
    const html = await page.content();
    const pageTitle = await page.title();

    if (isBotChallengePage(html, pageTitle)) {
      console.log(`Bot challenge detected for ${url}, skipping browser extraction`);
      return applyUrlFallbacks({
        title: null,
        description: null,
        image: null,
        domain: null
      }, url);
    }

    const $ = cheerio.load(html);

    const metadata = stripBotChallengeMetadata({
      title: extractTitle($, url),
      description: extractDescription($, url),
      image: await extractImage($, url, html),
      domain: extractDomain($, url) || extractDomainFromUrl(url)
    });

    if (!metadata.image) {
      console.log(`No image found in browser for ${url}, capturing screenshot`);
      metadata.image = await saveScreenshotFromPage(page);
    }

    return metadata;
  });
};

// Common set of User-Agents for all URLs
const USER_AGENTS = [
  'Mozilla/5.0 (compatible; Googlebot/2.1; +http://www.google.com/bot.html)',
  'Mozilla/5.0 (compatible; LinkPreviewBot/1.0; +http://example.com/bot)',
  'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36'
];

// Google Maps serves rich OG metadata only to social preview crawlers
const GOOGLE_MAPS_USER_AGENTS = [
  'facebookexternalhit/1.1 (+http://www.facebook.com/externalhit_uatext.php)',
  'Twitterbot/1.0',
  'Slackbot-LinkExpanding 1.0 (+https://api.slack.com/robots)',
  'Mozilla/5.0 (compatible; Discordbot/2.0; +https://discordapp.com)'
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
const getImageDimensions = async (imageUrl, options = {}) => {
  const maxArea = options.maxArea ?? MAX_IMAGE_AREA;
  const maxAspectRatio = options.maxAspectRatio ?? MAX_ASPECT_RATIO;

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
        area <= maxArea &&
        aspectRatioWidth <= maxAspectRatio &&
        aspectRatioHeight <= maxAspectRatio;

  } catch (e) {
    return false;
  }
};

// Amazon product photos are often larger than the generic preview max area
const AMAZON_MAX_IMAGE_AREA = 4000 * 4000;
const isValidAmazonImage = async (imageUrl) => {
  return getImageDimensions(imageUrl, {
    maxArea: AMAZON_MAX_IMAGE_AREA,
    maxAspectRatio: 4
  });
};

// Check if URL is from Amazon
const isAmazonUrl = (url) => {
  try {
    const urlObj = new URL(url);
    return urlObj.hostname.includes('amazon.');
  } catch (e) {
    return false;
  }
};

// Check if URL is a Google Maps place or search page
const isGoogleMapsUrl = (url) => {
  try {
    const urlObj = new URL(url);
    return urlObj.hostname.includes('google.') && urlObj.pathname.includes('/maps/');
  } catch (e) {
    return false;
  }
};

const getUserAgentsForUrl = (url) => {
  if (isGoogleMapsUrl(url)) {
    return [...GOOGLE_MAPS_USER_AGENTS, ...USER_AGENTS];
  }
  return USER_AGENTS;
};

const isGenericGoogleMapsImage = (imageUrl) => {
  return imageUrl && imageUrl.includes('maps/about/images/icons/maps_');
};

const isGoogleMapsStaticMapImage = (imageUrl) => {
  return imageUrl && imageUrl.includes('/maps/api/staticmap');
};

const getGoogleMapsImageScore = (imageUrl) => {
  if (!imageUrl) return -1;
  if (isGenericGoogleMapsImage(imageUrl)) return 0;
  if (/googleusercontent\.com\/(gps-cs-s|gpms-cs-s|p\/)/.test(imageUrl)) return 100;
  if (imageUrl.includes('googleusercontent.com')) return 50;
  if (isGoogleMapsStaticMapImage(imageUrl)) return 10;
  return 20;
};

const addGoogleMapsImageCandidate = (candidates, url, baseUrl) => {
  const resolvedUrl = resolveUrl(url, baseUrl);
  if (resolvedUrl) {
    candidates.add(resolvedUrl);
  }
};

const extractGoogleMapsImageCandidates = ($, html, baseUrl) => {
  const candidates = new Set();

  addGoogleMapsImageCandidate(candidates, $('meta[property="og:image"]').attr('content'), baseUrl);
  addGoogleMapsImageCandidate(candidates, $('meta[name="twitter:image"]').attr('content'), baseUrl);
  addGoogleMapsImageCandidate(candidates, $('link[rel="image_src"]').attr('href'), baseUrl);

  for (const match of html.matchAll(/https:\/\/lh3\.googleusercontent\.com\/(?:gps-cs-s|gpms-cs-s|p\/)[^"'\\s]+/g)) {
    candidates.add(match[0]);
  }

  return [...candidates];
};

const extractBestGoogleMapsImage = async ($, html, baseUrl) => {
  const candidates = extractGoogleMapsImageCandidates($, html, baseUrl);
  let bestImage = null;
  let bestScore = -1;

  for (const candidate of candidates) {
    const score = getGoogleMapsImageScore(candidate);
    if (score <= bestScore) {
      continue;
    }

    if (await getImageDimensions(candidate)) {
      bestImage = candidate;
      bestScore = score;
    }
  }

  return bestImage;
};

const shouldReplaceGoogleMapsImage = (currentImage, nextImage) => {
  if (!nextImage || isGenericGoogleMapsImage(nextImage)) {
    return false;
  }

  return getGoogleMapsImageScore(nextImage) > getGoogleMapsImageScore(currentImage);
};

// Fallback: extract place name from /maps/place/Name/@... URL path
const extractGoogleMapsPlaceNameFromUrl = (url) => {
  try {
    const match = url.match(/\/maps\/place\/([^/@?]+)/);
    if (match) {
      return cleanText(decodeURIComponent(match[1].replace(/\+/g, ' ')));
    }
  } catch (e) {
    // Ignore malformed URLs
  }
  return null;
};

const GENERIC_PATH_SEGMENTS = new Set([
  'index', 'home', 'default', 'main', 'page', 'view', 'article', 'posts', 'watch', 'embed'
]);

const formatUrlSegment = (segment) => {
  if (!segment) return null;

  let decoded;
  try {
    decoded = decodeURIComponent(segment);
  } catch (e) {
    decoded = segment;
  }

  decoded = decoded.replace(/\.(html?|php|aspx?|jsp)$/i, '');
  decoded = decoded.replace(/[-_+]+/g, ' ');
  return cleanText(decoded);
};

const toTitleCase = (text) => {
  if (!text) return null;
  return text.replace(/\b[\p{L}\p{N}]+\b/gu, (word) =>
    word.charAt(0).toUpperCase() + word.slice(1).toLowerCase()
  );
};

const isOpaquePathSegment = (segment) => {
  if (/^\d+$/.test(segment)) return true;
  if (/^[a-f0-9-]{20,}$/i.test(segment)) return true;
  if (/^B[0-9A-Z]{8,}$/i.test(segment)) return true;
  return false;
};

// Fallback: derive a readable title from the URL when HTML metadata is unavailable
const extractTitleFromUrl = (url) => {
  const mapsTitle = extractGoogleMapsPlaceNameFromUrl(url);
  if (mapsTitle) return mapsTitle;

  try {
    const urlObj = new URL(url);

    if (urlObj.hostname.includes('amazon.')) {
      const amazonMatch = urlObj.pathname.match(/\/([^/]+)\/dp\//)
        || urlObj.pathname.match(/\/gp\/product\/([^/]+)/);
      if (amazonMatch && !['dp', 'gp'].includes(amazonMatch[1])) {
        const formatted = formatUrlSegment(amazonMatch[1]);
        if (formatted) return toTitleCase(formatted);
      }
    }

    const segments = urlObj.pathname.split('/').filter(Boolean);
    for (let i = segments.length - 1; i >= 0; i--) {
      const segment = segments[i];
      if (isOpaquePathSegment(segment)) continue;

      const formatted = formatUrlSegment(segment);
      if (!formatted || formatted.length < 2) continue;
      if (GENERIC_PATH_SEGMENTS.has(formatted.toLowerCase())) continue;

      return toTitleCase(formatted);
    }

    const hostname = urlObj.hostname.replace(/^www\./, '');
    const domainParts = hostname.split('.');
    if (domainParts.length >= 2) {
      const siteName = formatUrlSegment(domainParts[domainParts.length - 2]);
      if (siteName && siteName.length > 1) {
        return toTitleCase(siteName);
      }
    }

    return toTitleCase(formatUrlSegment(hostname.replace(/\./g, ' ')));
  } catch (e) {
    return null;
  }
};

const applyUrlFallbacks = (metadata, url) => {
  if (!metadata.title) {
    metadata.title = extractTitleFromUrl(url);
  }
  if (!metadata.domain) {
    metadata.domain = extractDomainFromUrl(url);
  }
  return metadata;
};

// Fallback: parse embedded XSSI JSON that Google includes in the initial HTML
const extractGoogleMapsMetadataFromHtml = (html) => {
  const metadata = { title: null, description: null };

  const xssiIndex = html.indexOf(")]}'");
  if (xssiIndex >= 0) {
    const chunk = html.substring(xssiIndex, xssiIndex + 8000);
    const placeMatch = chunk.match(/\["0x[a-f0-9]+:0x[a-f0-9]+","([^"]+)"/i);
    if (placeMatch) {
      metadata.title = cleanText(placeMatch[1]);
    }
  }

  return metadata;
};

const isGenericAmazonTitle = (title) => {
  if (!title) return true;
  const normalized = title.toLowerCase().trim();
  return /^(amazon(\.(de|com|co\.uk|fr|it|es|nl|pl|se|com\.br|com\.mx|ca|in|com\.au))?|amazon\.com:?\s*amazon\.com)$/i.test(normalized);
};

const cleanAmazonTitle = (title) => {
  const cleaned = cleanText(title);
  if (!cleaned || isGenericAmazonTitle(cleaned)) return null;
  // Strip leading "Amazon.de: " / "Amazon.com: " prefixes when the product name follows
  const withoutPrefix = cleaned.replace(/^Amazon(?:\.[a-z.]+)?:\s*/i, '').trim();
  return withoutPrefix && !isGenericAmazonTitle(withoutPrefix) ? withoutPrefix : cleaned;
};

// Extract Amazon-specific title
const extractAmazonTitle = ($) => {
  // Amazon product title
  const productTitle = cleanAmazonTitle($('#productTitle').text());
  if (productTitle) return productTitle;

  // Alternative Amazon selectors
  const titleSpan = cleanAmazonTitle($('span.a-size-large.product-title-word-break').first().text());
  if (titleSpan) return titleSpan;

  const titleH1 = cleanAmazonTitle($('h1.a-size-large').first().text());
  if (titleH1) return titleH1;

  const ogTitle = cleanAmazonTitle($('meta[property="og:title"]').attr('content'));
  if (ogTitle) return ogTitle;

  const docTitle = cleanAmazonTitle($('title').text());
  if (docTitle) return docTitle;

  return null;
};

// Extract title following the specified rules
const extractTitle = ($, url) => {
  // For Amazon, try Amazon-specific selectors first
  if (url && isAmazonUrl(url)) {
    const amazonTitle = extractAmazonTitle($);
    if (amazonTitle) return amazonTitle;
  }

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

const pickAmazonImageCandidate = async (url, baseUrl) => {
  const resolvedUrl = resolveUrl(url, baseUrl);
  if (resolvedUrl && await isValidAmazonImage(resolvedUrl)) {
    return resolvedUrl;
  }
  return null;
};

const extractAmazonDynamicImage = async (el, baseUrl) => {
  const dynamic = el.attr('data-a-dynamic-image');
  if (!dynamic) return null;

  try {
    const parsed = JSON.parse(dynamic);
    const candidates = Object.keys(parsed);
    // Prefer the largest declared candidate
    candidates.sort((a, b) => {
      const aSize = (parsed[a][0] || 0) * (parsed[a][1] || 0);
      const bSize = (parsed[b][0] || 0) * (parsed[b][1] || 0);
      return bSize - aSize;
    });

    for (const candidate of candidates) {
      const accepted = await pickAmazonImageCandidate(candidate, baseUrl);
      if (accepted) return accepted;
    }
  } catch (e) {
    // Ignore malformed dynamic image JSON
  }

  return null;
};

// Extract Amazon-specific image
const extractAmazonImage = async ($, baseUrl) => {
  const landingEl = $('#landingImage');
  const candidates = [
    landingEl.attr('data-old-hires'),
    landingEl.attr('data-src'),
    landingEl.attr('src'),
    $('#imgBlkFront').attr('data-old-hires'),
    $('#imgBlkFront').attr('data-src'),
    $('#imgBlkFront').attr('src'),
    $('#main-image img').first().attr('data-old-hires'),
    $('#main-image img').first().attr('data-src'),
    $('#main-image img').first().attr('src'),
    $('img[data-a-image-name="landingImage"]').attr('data-old-hires'),
    $('img[data-a-image-name="landingImage"]').attr('data-src'),
    $('img[data-a-image-name="landingImage"]').attr('src'),
    $('meta[property="og:image"]').attr('content'),
    $('meta[name="twitter:image"]').attr('content')
  ];

  for (const candidate of candidates) {
    if (!candidate || candidate.startsWith('data:')) continue;
    const accepted = await pickAmazonImageCandidate(candidate, baseUrl);
    if (accepted) return accepted;
  }

  const dynamicImage = await extractAmazonDynamicImage(landingEl, baseUrl);
  if (dynamicImage) return dynamicImage;

  return null;
};

// Extract image following the specified rules
const extractImage = async ($, baseUrl, html = null) => {
  // For Amazon, try Amazon-specific selectors first
  if (isAmazonUrl(baseUrl)) {
    const amazonImage = await extractAmazonImage($, baseUrl);
    if (amazonImage) return amazonImage;
  }

  if (isGoogleMapsUrl(baseUrl) && html) {
    const googleMapsImage = await extractBestGoogleMapsImage($, html, baseUrl);
    if (googleMapsImage) return googleMapsImage;
  }

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
    
    const src = $(el).attr('src') || $(el).attr('data-src');
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

// Extract Amazon-specific description
const extractAmazonDescription = ($) => {
  // Amazon product description
  const productDescription = $('#productDescription').text();
  if (productDescription) return cleanText(productDescription);

  // Amazon feature bullets
  const featureBullets = $('#feature-bullets ul').first().text();
  if (featureBullets) return cleanText(featureBullets);

  // Amazon product overview
  const productOverview = $('#productOverview_feature_div').text();
  if (productOverview) return cleanText(productOverview);

  return null;
};

// Extract description following the specified rules
const extractDescription = ($, url) => {
  // For Amazon, try Amazon-specific selectors first
  if (url && isAmazonUrl(url)) {
    const amazonDesc = extractAmazonDescription($);
    if (amazonDesc) return amazonDesc;
  }

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

app.get('/screenshots/:filename', async (req, res) => {
  const filename = path.basename(req.params.filename);
  if (!/^[a-zA-Z0-9_-]+\.webp$/.test(filename)) {
    return res.status(400).send('Invalid filename');
  }

  const filePath = path.join(SCREENSHOT_DIR, filename);

  try {
    await access(filePath);
    res.type('image/webp');
    return res.sendFile(filePath);
  } catch (e) {
    return res.status(404).send('Not found');
  }
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

    // For Amazon URLs, use headless browser first to render JavaScript
    const isAmazon = isAmazonUrl(url);
    let html = null;
    
    if (isAmazon) {
      console.log(`Using single-file browser for Amazon URL: ${url}`);
      html = await renderWithBrowser(url);
      if (html) {
        const $ = cheerio.load(html);
        
        // Extract metadata from rendered HTML
        const currentTitle = extractTitle($, url);
        if (currentTitle) {
          bestMetadata.title = currentTitle;
        }

        const currentDescription = extractDescription($, url);
        if (currentDescription) {
          bestMetadata.description = currentDescription;
        }

        const currentImage = await extractImage($, url);
        if (currentImage) {
          bestMetadata.image = currentImage;
        }

        const currentDomain = extractDomain($, url);
        if (currentDomain) {
          bestMetadata.domain = currentDomain;
        }

        applyUrlFallbacks(bestMetadata, url);

        // Amazon uses its own renderer — never fall through to Puppeteer screenshots
        if (bestMetadata.title || bestMetadata.image) {
          return res.json(bestMetadata);
        }
      }
    }

    // Try each User-Agent (fallback or for non-Amazon URLs)
    let htmlValidationFailed = !html;
    let lastHtml = html;
    for (const userAgent of getUserAgentsForUrl(url)) {
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
        
        const htmlData = axiosResponse.data;
        const isHtmlContent = typeof htmlData === 'string' && 
                            (htmlData.trim().toLowerCase().startsWith('<!doctype html') || 
                             htmlData.trim().toLowerCase().startsWith('<html'));
        
        if (!isHtmlContentType || !isHtmlContent) {
          console.log(`Skipping non-HTML content from ${url} (Content-Type: ${contentType})`);
          continue; // Skip to next User-Agent
        }
        
        htmlValidationFailed = false;
        lastHtml = htmlData;
        const $ = cheerio.load(htmlData);
        
        // Try to get each field, only update if we don't have it yet or if it's better
        const currentTitle = extractTitle($, url);
        if (currentTitle && (!bestMetadata.title || currentTitle.length > bestMetadata.title.length)) {
          bestMetadata.title = currentTitle;
        }

        const currentDescription = extractDescription($, url);
        if (currentDescription && (!bestMetadata.description || currentDescription.length > bestMetadata.description.length)) {
          bestMetadata.description = currentDescription;
        }

        const currentImage = await extractImage($, url, htmlData);
        if (isGoogleMapsUrl(url)) {
          if (shouldReplaceGoogleMapsImage(bestMetadata.image, currentImage)) {
            bestMetadata.image = currentImage;
          }
        } else if (currentImage && !isGenericGoogleMapsImage(currentImage) &&
            (!bestMetadata.image || isGenericGoogleMapsImage(bestMetadata.image) ||
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

        if (isGoogleMapsUrl(url) && getGoogleMapsImageScore(bestMetadata.image) >= 100) {
          break;
        }
      } catch (error) {
        // Continue with next User-Agent
      }
    }

    // Google Maps fallbacks when social preview crawlers did not return full metadata
    if (isGoogleMapsUrl(url)) {
      if (!bestMetadata.title) {
        bestMetadata.title = extractGoogleMapsPlaceNameFromUrl(url);
      }

      if (lastHtml && !bestMetadata.title) {
        const embeddedMetadata = extractGoogleMapsMetadataFromHtml(lastHtml);
        if (embeddedMetadata.title) {
          bestMetadata.title = embeddedMetadata.title;
        }
      }

      if (isGenericGoogleMapsImage(bestMetadata.image)) {
        bestMetadata.image = null;
      }
    }

    // axios could not fetch HTML (e.g. Cloudflare) — try Chromium
    // Amazon has its own single-file path and must not fall into Puppeteer screenshots
    if (htmlValidationFailed && !isAmazon) {
      console.log(`HTML fetch failed for ${url}, trying browser fallback`);
      const browserMetadata = await fetchMetadataWithBrowser(url);

      if (browserMetadata) {
        if (browserMetadata.title) bestMetadata.title = browserMetadata.title;
        if (browserMetadata.description) bestMetadata.description = browserMetadata.description;
        if (browserMetadata.image) bestMetadata.image = browserMetadata.image;
        if (browserMetadata.domain) bestMetadata.domain = browserMetadata.domain;
      } else {
        console.log(`Browser fallback failed for ${url}, trying screenshot only`);
        const screenshotUrl = await captureScreenshot(url);
        if (screenshotUrl) {
          bestMetadata.image = screenshotUrl;
        }
      }

      if (!bestMetadata.domain) {
        bestMetadata.domain = extractDomainFromUrl(url);
      }
    }

    if (!bestMetadata.image && !htmlValidationFailed && !isAmazonUrl(url)) {
      console.log(`No image found for ${url}, capturing screenshot fallback`);
      const screenshotUrl = await captureScreenshot(url);
      if (screenshotUrl) {
        bestMetadata.image = screenshotUrl;
      }
    }

    applyUrlFallbacks(bestMetadata, url);

    // Always return the metadata, even if some fields are null
    res.json(bestMetadata);
  } catch (err) {
    // In case of any errors, return URL-derived fallbacks with 200 status
    res.json(applyUrlFallbacks({
      title: null,
      description: null,
      image: null,
      domain: null
    }, url));
  }
});

// Helper function to check if we have all required metadata
const hasAllRequiredMetadata = (metadata) => {
  return metadata.title && metadata.image && metadata.domain;
};

app.listen(PORT, () => {
  console.log(`Filedpapers-Metascraper up and running.`);
});
