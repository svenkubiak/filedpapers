const express = require('express');
const axios = require('axios');
const sharp = require('sharp');
const puppeteer = require('puppeteer-core');
const metascraperFactory = require('metascraper');
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

// metascraper: title, description, image, canonical url (+ amazon-specific rules)
const metascraper = metascraperFactory([
  require('metascraper-amazon')(),
  require('metascraper-title')(),
  require('metascraper-description')(),
  require('metascraper-image')(),
  require('metascraper-url')()
]);

const EMPTY_METADATA = Object.freeze({
  title: null,
  description: null,
  image: null,
  domain: null
});

const MAX_IMAGE_SIZE = 1024;
const MAX_IMAGE_AREA = MAX_IMAGE_SIZE * MAX_IMAGE_SIZE;
const MAX_IMAGE_CANDIDATES = 10;
const MIN_IMAGE_SIZE = 100;
const MIN_IMAGE_AREA = MIN_IMAGE_SIZE * MIN_IMAGE_SIZE;
const MAX_ASPECT_RATIO = 3;
const AMAZON_MAX_IMAGE_AREA = 4000 * 4000;
const BROWSER_VIEWPORT = { width: 1280, height: 720 };
const BROWSER_TIMEOUT_MS = 20000;
const BROWSER_SETTLE_MS = 1000;
const SCREENSHOT_DIR = path.join(os.tmpdir(), 'metascraper-screenshots');
const SCREENSHOT_TTL_MS = 60 * 60 * 1000;
const SINGLE_FILE_BIN = path.join(__dirname, 'node_modules', '.bin', 'single-file');

const USER_AGENTS = [
  'Mozilla/5.0 (compatible; Googlebot/2.1; +http://www.google.com/bot.html)',
  'Mozilla/5.0 (compatible; LinkPreviewBot/1.0; +http://example.com/bot)',
  'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36'
];

const GOOGLE_MAPS_USER_AGENTS = [
  'facebookexternalhit/1.1 (+http://www.facebook.com/externalhit_uatext.php)',
  'Twitterbot/1.0',
  'Slackbot-LinkExpanding 1.0 (+https://api.slack.com/robots)',
  'Mozilla/5.0 (compatible; Discordbot/2.0; +https://discordapp.com)'
];

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

const GENERIC_PATH_SEGMENTS = new Set([
  'index', 'home', 'default', 'main', 'page', 'view', 'article', 'posts', 'watch', 'embed'
]);

const findChromiumPath = async () => {
  if (process.env.CHROME_BIN) {
    try {
      await access(process.env.CHROME_BIN);
      return process.env.CHROME_BIN;
    } catch (e) {
      // continue
    }
  }

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
      continue;
    }
  }

  try {
    const { stdout } = await execAsync('which chromium || which chromium-browser || which google-chrome || which google-chrome-stable');
    const foundPath = stdout.trim();
    if (foundPath) return foundPath;
  } catch (e) {
    // ignore
  }

  return null;
};

const getChromiumArgs = () => {
  const flags = process.env.CHROMIUM_FLAGS || '--no-sandbox --disable-setuid-sandbox --disable-dev-shm-usage --disable-gpu';
  return flags.split(/\s+/).filter(Boolean);
};

const cleanText = (text) => {
  return text ? text.trim().replace(/\s+/g, ' ') : null;
};

const resolveUrl = (url, baseUrl) => {
  try {
    if (!url || url.startsWith('data:')) return null;
    const baseUrlWithoutFragment = baseUrl.split('#')[0];
    return new URL(url, baseUrlWithoutFragment).href;
  } catch (e) {
    return null;
  }
};

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
    const validFormats = ['png', 'jpeg', 'jpg', 'jp2', 'webp'];
    if (!validFormats.includes(metadata.format)) return false;

    const area = metadata.width * metadata.height;
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

const isValidAmazonImage = async (imageUrl) => {
  return getImageDimensions(imageUrl, {
    maxArea: AMAZON_MAX_IMAGE_AREA,
    maxAspectRatio: 4
  });
};

const AMAZON_SHORT_HOSTS = new Set([
  'a.co',
  'amzn.to',
  'amzn.eu',
  'amzn.asia'
]);

const isAmazonShortUrl = (url) => {
  try {
    const hostname = new URL(url).hostname.replace(/^www\./, '');
    if (AMAZON_SHORT_HOSTS.has(hostname)) return true;
    return /^amzn\.[a-z.]+$/.test(hostname);
  } catch (e) {
    return false;
  }
};

const isAmazonUrl = (url) => {
  try {
    const hostname = new URL(url).hostname.replace(/^www\./, '');
    if (hostname.includes('amazon.')) return true;
    return isAmazonShortUrl(url);
  } catch (e) {
    return false;
  }
};

const resolveFinalUrl = async (url) => {
  try {
    const response = await axios.get(url, {
      maxRedirects: 10,
      timeout: 15000,
      headers: {
        'User-Agent': USER_AGENTS[2],
        'Accept': 'text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8'
      },
      responseType: 'text',
      validateStatus: (status) => status >= 200 && status < 400
    });

    return response.request?.res?.responseUrl
      || response.request?.responseURL
      || url;
  } catch (e) {
    return url;
  }
};

const isGoogleMapsUrl = (url) => {
  try {
    const urlObj = new URL(url);
    return urlObj.hostname.includes('google.') && urlObj.pathname.includes('/maps/');
  } catch (e) {
    return false;
  }
};

const parseMastodonUrl = (url) => {
  try {
    const urlObj = new URL(url);
    const statusMatch = urlObj.pathname.match(/^(?:\/web)?\/@[^/]+\/(\d+)\/?$/);
    if (statusMatch) {
      return {
        type: 'status',
        statusId: statusMatch[1],
        apiUrl: `${urlObj.origin}/api/v1/statuses/${statusMatch[1]}`
      };
    }

    const profileMatch = urlObj.pathname.match(/^(?:\/web)?\/@([^/]+)\/?$/);
    if (profileMatch) {
      return {
        type: 'profile',
        acct: profileMatch[1],
        apiUrl: `${urlObj.origin}/api/v1/accounts/lookup?acct=${encodeURIComponent(profileMatch[1])}`
      };
    }

    return null;
  } catch (e) {
    return null;
  }
};

const stripHtml = (html) => {
  if (!html) return null;
  return cleanText(html
    .replace(/<br\s*\/?>/gi, ' ')
    .replace(/<\/p>/gi, ' ')
    .replace(/<[^>]+>/g, '')
    .replace(/&nbsp;/g, ' ')
    .replace(/&amp;/g, '&')
    .replace(/&lt;/g, '<')
    .replace(/&gt;/g, '>')
    .replace(/&quot;/g, '"')
    .replace(/&#39;/g, "'"));
};

const getUserAgentsForUrl = (url) => {
  if (isGoogleMapsUrl(url)) {
    return [...GOOGLE_MAPS_USER_AGENTS, ...USER_AGENTS];
  }
  return USER_AGENTS;
};

const extractDomainFromUrl = (url) => {
  try {
    const domain = new URL(url).hostname;
    return domain.startsWith('www.') ? domain.substring(4) : domain;
  } catch (e) {
    return null;
  }
};

const isBotChallengePage = (html, title) => {
  const lowerHtml = (html || '').toLowerCase();
  const lowerTitle = (title || '').toLowerCase();

  if (BOT_CHALLENGE_TITLES.some((entry) => lowerTitle.includes(entry))) {
    return true;
  }

  return BOT_CHALLENGE_INDICATORS.some((indicator) => lowerHtml.includes(indicator));
};

const isGenericAmazonTitle = (title) => {
  if (!title) return true;
  const normalized = title.toLowerCase().trim();
  return /^(amazon(\.(de|com|co\.uk|fr|it|es|nl|pl|se|com\.br|com\.mx|ca|in|com\.au))?|amazon\.com:?\s*amazon\.com)$/i.test(normalized);
};

const cleanAmazonTitle = (title) => {
  const cleaned = cleanText(title);
  if (!cleaned || isGenericAmazonTitle(cleaned)) return null;
  const withoutPrefix = cleaned.replace(/^Amazon(?:\.[a-z.]+)?:\s*/i, '').trim();
  return withoutPrefix && !isGenericAmazonTitle(withoutPrefix) ? withoutPrefix : cleaned;
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
  if (resolvedUrl) candidates.add(resolvedUrl);
};

const collectGoogleMapsImageCandidates = (html, baseUrl, seedImage) => {
  const candidates = new Set();
  addGoogleMapsImageCandidate(candidates, seedImage, baseUrl);

  for (const match of html.matchAll(/https:\/\/lh3\.googleusercontent\.com\/(?:gps-cs-s|gpms-cs-s|p\/)[^"'\\s]+/g)) {
    candidates.add(match[0]);
  }

  return [...candidates];
};

const resolveGoogleMapsImage = async (html, baseUrl, currentImage) => {
  const candidates = collectGoogleMapsImageCandidates(html, baseUrl, currentImage);
  let bestImage = currentImage;
  let bestScore = getGoogleMapsImageScore(currentImage);

  for (const candidate of candidates) {
    const score = getGoogleMapsImageScore(candidate);
    if (score <= bestScore) continue;

    if (await getImageDimensions(candidate)) {
      bestImage = candidate;
      bestScore = score;
    }
  }

  return isGenericGoogleMapsImage(bestImage) ? null : bestImage;
};

const shouldReplaceGoogleMapsImage = (currentImage, nextImage) => {
  if (!nextImage || isGenericGoogleMapsImage(nextImage)) return false;
  return getGoogleMapsImageScore(nextImage) > getGoogleMapsImageScore(currentImage);
};

const extractGoogleMapsPlaceNameFromUrl = (url) => {
  try {
    const match = url.match(/\/maps\/place\/([^/@?]+)/);
    if (match) {
      return cleanText(decodeURIComponent(match[1].replace(/\+/g, ' ')));
    }
  } catch (e) {
    // ignore
  }
  return null;
};

const extractGoogleMapsMetadataFromHtml = (html) => {
  const metadata = { title: null };

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

const validateImageUrl = async (imageUrl, pageUrl) => {
  if (!imageUrl) return null;
  const ok = isAmazonUrl(pageUrl)
    ? await isValidAmazonImage(imageUrl)
    : await getImageDimensions(imageUrl);
  return ok ? imageUrl : null;
};

const findValidImageInHtml = async (html, baseUrl, pageUrl) => {
  if (!html) return null;

  const imgTags = html.match(/<img\b[^>]*>/gi) || [];
  let candidates = 0;

  for (const tag of imgTags) {
    if (candidates >= MAX_IMAGE_CANDIDATES) break;

    const srcMatch = tag.match(/\b(?:src|data-src)=["']([^"']+)["']/i);
    if (!srcMatch) continue;

    candidates++;
    const resolved = resolveUrl(srcMatch[1], baseUrl);
    if (!resolved || resolved.startsWith('data:')) continue;

    const valid = await validateImageUrl(resolved, pageUrl);
    if (valid) return valid;
  }

  return null;
};

const normalizeScrapedMetadata = async (scraped, url, html) => {
  let title = cleanText(scraped.title);
  if (isAmazonUrl(url)) {
    title = cleanAmazonTitle(title);
  }
  if (title && BOT_CHALLENGE_TITLES.some((entry) => title.toLowerCase().includes(entry))) {
    title = null;
  }

  let description = cleanText(scraped.description);
  let image = await validateImageUrl(scraped.image, url);

  if (isGoogleMapsUrl(url) && html) {
    image = await resolveGoogleMapsImage(html, url, image);
  }

  if (!image && html && !isAmazonUrl(url) && !isGoogleMapsUrl(url)) {
    image = await findValidImageInHtml(html, url, url);
  }

  const domain = scraped.url
    ? extractDomainFromUrl(scraped.url)
    : extractDomainFromUrl(url);

  return { title, description, image, domain };
};

const enhanceMetadataForSite = (metadata, url, html) => {
  if (isGoogleMapsUrl(url)) {
    if (!metadata.title) {
      metadata.title = extractGoogleMapsPlaceNameFromUrl(url);
    }
    if (html && !metadata.title) {
      const embeddedMetadata = extractGoogleMapsMetadataFromHtml(html);
      if (embeddedMetadata.title) {
        metadata.title = embeddedMetadata.title;
      }
    }
    if (isGenericGoogleMapsImage(metadata.image)) {
      metadata.image = null;
    }
  }

  return metadata;
};

const extractFromHtml = async (url, html) => {
  if (!html) {
    return { ...EMPTY_METADATA };
  }

  if (isBotChallengePage(html, null)) {
    console.log(`Bot challenge HTML detected for ${url}`);
    return applyUrlFallbacks({ ...EMPTY_METADATA }, url);
  }

  let scraped;
  try {
    scraped = await metascraper({ url, html });
  } catch (error) {
    console.error('metascraper extraction error:', error);
    scraped = {};
  }

  return normalizeScrapedMetadata(scraped, url, html);
};

const fetchImageFromLinkedUrl = async (linkUrl) => {
  if (!linkUrl) return null;

  try {
    const response = await axios.get(linkUrl, {
      headers: {
        'User-Agent': USER_AGENTS[1],
        'Accept': 'text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8',
        'Accept-Language': 'de,en;q=0.8'
      },
      timeout: 15000,
      maxRedirects: 5
    });

    const html = response.data;
    if (typeof html !== 'string') return null;

    const metadata = await extractFromHtml(linkUrl, html);
    return metadata.image || null;
  } catch (error) {
    console.log(`Linked URL preview failed for ${linkUrl}: ${error.message}`);
    return null;
  }
};

const buildMastodonAccountTitle = (account) => {
  const username = account.username;
  const acct = account.acct || username;
  const displayName = (account.display_name || '').trim() || username;
  return acct ? cleanText(`${displayName} (@${acct})`) : null;
};

const fetchMastodonProfileImage = async (account, pageUrl) => {
  for (const candidate of [account.header, account.avatar]) {
    const image = await validateImageUrl(candidate, pageUrl);
    if (image) return image;
  }
  return null;
};

const fetchMastodonStatusMetadata = async (status, pageUrl) => {
  const account = status.account || {};
  const title = buildMastodonAccountTitle(account);

  let description = stripHtml(status.content);
  if (description && description.length > 300) {
    description = `${description.slice(0, 297)}...`;
  }

  let image = null;
  if (status.media_attachments?.length) {
    const media = status.media_attachments.find((entry) => entry.type === 'image')
      || status.media_attachments[0];
    image = media.preview_url || media.url;
  }

  // Prefer OG image from the linked page — Mastodon's cached card.image can be a
  // generic/warning preview; a screenshot would capture the "link verlassen" UI.
  if (!image && status.card?.url) {
    image = await fetchImageFromLinkedUrl(status.card.url);
  }
  if (!image && status.card?.image) {
    image = status.card.image;
  }
  if (!image && account.header) {
    image = account.header;
  }
  if (!image && account.avatar) {
    image = account.avatar;
  }

  image = await validateImageUrl(image, pageUrl);

  return { title, description, image };
};

const fetchMastodonProfileMetadata = async (account, pageUrl) => {
  const title = buildMastodonAccountTitle(account);

  let description = stripHtml(account.note);
  if (description && description.length > 300) {
    description = `${description.slice(0, 297)}...`;
  }

  const image = await fetchMastodonProfileImage(account, pageUrl);
  return { title, description, image };
};

const fetchMastodonMetadata = async (url) => {
  const parsed = parseMastodonUrl(url);
  if (!parsed) return null;

  try {
    const response = await axios.get(parsed.apiUrl, {
      headers: {
        'User-Agent': USER_AGENTS[1],
        'Accept': 'application/json'
      },
      timeout: 15000
    });

    let metadata;
    if (parsed.type === 'profile') {
      const account = response.data;
      if (!account?.id) return null;
      metadata = await fetchMastodonProfileMetadata(account, url);
    } else {
      const status = response.data;
      if (!status?.id) return null;
      metadata = await fetchMastodonStatusMetadata(status, url);
    }

    return {
      ...metadata,
      domain: extractDomainFromUrl(url)
    };
  } catch (error) {
    console.log(`Mastodon API fetch failed for ${url}: ${error.message}`);
    return null;
  }
};

const mergeMetadata = (best, next, url) => {
  if (!next) return best;

  if (next.title && (!best.title || next.title.length > best.title.length)) {
    best.title = next.title;
  }

  if (next.description && (!best.description || next.description.length > best.description.length)) {
    best.description = next.description;
  }

  if (isGoogleMapsUrl(url)) {
    if (shouldReplaceGoogleMapsImage(best.image, next.image)) {
      best.image = next.image;
    }
  } else if (next.image && !best.image) {
    best.image = next.image;
  }

  if (next.domain && !best.domain) {
    best.domain = next.domain;
  }

  return best;
};

const hasAllRequiredMetadata = (metadata) => {
  return metadata.title && metadata.image && metadata.domain;
};

const launchBrowser = async () => {
  const chromiumPath = await findChromiumPath();
  if (!chromiumPath) return null;

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
        // ignore
      }
    }));
  } catch (e) {
    // ignore
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
      return applyUrlFallbacks({ ...EMPTY_METADATA }, url);
    }

    const metadata = await extractFromHtml(url, html);

    if (!metadata.image) {
      console.log(`No image found in browser for ${url}, capturing screenshot`);
      metadata.image = await saveScreenshotFromPage(page);
    }

    return metadata;
  });
};

// Amazon pages: single-file-cli for JS-rendered product HTML
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

app.get('/preview', async (req, res) => {
  const url = req.query.url;
  const lang = req.query.lang || 'en-US';
  if (!url) return res.status(400).json({ error: 'Missing ?url=' });

  try {
    let bestMetadata = { ...EMPTY_METADATA };

    let fetchUrl = url;
    if (isAmazonUrl(url) && isAmazonShortUrl(url)) {
      const resolvedUrl = await resolveFinalUrl(url);
      if (resolvedUrl && resolvedUrl !== url) {
        console.log(`Resolved Amazon short URL: ${url} -> ${resolvedUrl}`);
        fetchUrl = resolvedUrl;
      }
    }

    const isAmazon = isAmazonUrl(fetchUrl);
    const isMastodon = Boolean(parseMastodonUrl(fetchUrl));
    let html = null;

    if (isMastodon) {
      const mastodonMetadata = await fetchMastodonMetadata(fetchUrl) || { ...EMPTY_METADATA };
      applyUrlFallbacks(mastodonMetadata, fetchUrl);
      return res.json(mastodonMetadata);
    }

    if (isAmazon) {
      console.log(`Using single-file browser for Amazon URL: ${fetchUrl}`);
      html = await renderWithBrowser(fetchUrl);
      if (html) {
        bestMetadata = mergeMetadata(bestMetadata, await extractFromHtml(fetchUrl, html), fetchUrl);
        applyUrlFallbacks(bestMetadata, fetchUrl);

        if (bestMetadata.title || bestMetadata.image) {
          return res.json(bestMetadata);
        }
      }
    }

    let htmlValidationFailed = !html;
    let lastHtml = html;

    for (const userAgent of getUserAgentsForUrl(fetchUrl)) {
      try {
        const axiosResponse = await axios.get(fetchUrl, {
          headers: {
            'User-Agent': userAgent,
            'Accept': 'text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8',
            'Accept-Language': `${lang},en;q=0.8,*;q=0.5`,
            'Accept-Encoding': 'gzip, deflate, br',
            'Connection': 'keep-alive'
          },
          timeout: 15000,
          maxRedirects: 5
        });

        const contentType = axiosResponse.headers['content-type'] || '';
        const isHtmlContentType = contentType.includes('text/html') ||
          contentType.includes('application/xhtml+xml');

        const htmlData = axiosResponse.data;
        const isHtmlContent = typeof htmlData === 'string' &&
          (htmlData.trim().toLowerCase().startsWith('<!doctype html') ||
            htmlData.trim().toLowerCase().startsWith('<html'));

        if (!isHtmlContentType || !isHtmlContent) {
          console.log(`Skipping non-HTML content from ${fetchUrl} (Content-Type: ${contentType})`);
          continue;
        }

        htmlValidationFailed = false;
        lastHtml = htmlData;
        bestMetadata = mergeMetadata(bestMetadata, await extractFromHtml(fetchUrl, htmlData), fetchUrl);

        if (hasAllRequiredMetadata(bestMetadata)) break;
        if (isGoogleMapsUrl(fetchUrl) && getGoogleMapsImageScore(bestMetadata.image) >= 100) break;
      } catch (error) {
        // next User-Agent
      }
    }

    enhanceMetadataForSite(bestMetadata, fetchUrl, lastHtml);

    // Cloudflare / blocked HTML → Chromium (not for Amazon)
    if (htmlValidationFailed && !isAmazon) {
      console.log(`HTML fetch failed for ${fetchUrl}, trying browser fallback`);
      const browserMetadata = await fetchMetadataWithBrowser(fetchUrl);

      if (browserMetadata) {
        bestMetadata = mergeMetadata(bestMetadata, browserMetadata, fetchUrl);
      } else {
        console.log(`Browser fallback failed for ${fetchUrl}, trying screenshot only`);
        const screenshotUrl = await captureScreenshot(fetchUrl);
        if (screenshotUrl) {
          bestMetadata.image = screenshotUrl;
        }
      }
    }

    if (!bestMetadata.image && !htmlValidationFailed && !isAmazon && !isMastodon) {
      console.log(`No image found for ${fetchUrl}, capturing screenshot fallback`);
      const screenshotUrl = await captureScreenshot(fetchUrl);
      if (screenshotUrl) {
        bestMetadata.image = screenshotUrl;
      }
    }

    applyUrlFallbacks(bestMetadata, fetchUrl);
    res.json(bestMetadata);
  } catch (err) {
    console.error('Preview error:', err);
    res.json(applyUrlFallbacks({ ...EMPTY_METADATA }, url));
  }
});

app.listen(PORT, () => {
  console.log(`Filedpapers-Metascraper up and running.`);
});
