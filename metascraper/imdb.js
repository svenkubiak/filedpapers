'use strict';

const { safeAxios } = require('./ssrf-guard');

// Title pages answer scrapers with an empty WAF challenge, so the only title left
// on the URL is the tt/nm id. The suggestion endpoint still returns the real name and poster.
const SUGGESTION_ORIGIN = 'https://v3.sg.media-imdb.com';
const USER_AGENT = 'Mozilla/5.0 (compatible; LinkPreviewBot/1.0; +http://example.com/bot)';
const POSTER_WIDTHS = [600, 400];

const clean = (text) => (text ? String(text).trim().replace(/\s+/g, ' ') : null);

const isImdbUrl = (url) => {
  try {
    const host = new URL(url).hostname.replace(/^www\./, '');
    return host === 'imdb.com' || host.endsWith('.imdb.com');
  } catch (e) {
    return false;
  }
};

const parseImdbTarget = (url) => {
  try {
    const parsed = new URL(url);
    const host = parsed.hostname.replace(/^www\./, '');
    if (host !== 'imdb.com' && !host.endsWith('.imdb.com')) return null;

    const match = parsed.pathname.match(/\/((?:tt|nm)\d{5,})(?:\/|$)/i);
    if (!match) return null;

    const id = match[1].toLowerCase();
    return {
      id,
      apiUrl: `${SUGGESTION_ORIGIN}/suggestion/${id.charAt(0)}/${id}.json`
    };
  } catch (e) {
    return null;
  }
};

const imdbImageAtWidth = (imageUrl, width) => {
  if (!imageUrl) return null;
  if (!/\._V1_/i.test(imageUrl)) return imageUrl;
  return imageUrl.replace(/\._V1_[^.]*(\.[a-z0-9]+)$/i, `._V1_SX${width}_$1`);
};

const imdbDescription = (entry) => {
  const parts = [];
  if (entry.y) parts.push(String(entry.y));
  if (entry.s) parts.push(entry.s);
  return clean(parts.join(' · '));
};

const pickImdbEntry = (payload, id) => {
  const entries = payload?.d;
  if (!Array.isArray(entries)) return null;
  return entries.find((entry) => entry?.id?.toLowerCase() === id) || null;
};

const fetchImdbMetadata = async (url, validateImageUrl) => {
  const target = parseImdbTarget(url);
  if (!target) return null;

  try {
    const response = await safeAxios.get(target.apiUrl, {
      headers: {
        'User-Agent': USER_AGENT,
        'Accept': 'application/json'
      },
      timeout: 15000
    });

    const payload = typeof response.data === 'string' ? JSON.parse(response.data) : response.data;
    const entry = pickImdbEntry(payload, target.id);
    const title = clean(entry?.l);
    if (!title) return null;

    let image = null;
    const source = entry.i?.imageUrl;
    if (source && validateImageUrl) {
      const candidates = [...new Set(POSTER_WIDTHS.map((width) => imdbImageAtWidth(source, width)))];
      for (const candidate of candidates) {
        image = await validateImageUrl(candidate, url);
        if (image) break;
      }
    }

    return {
      title,
      description: imdbDescription(entry),
      image,
      domain: null
    };
  } catch (error) {
    console.log(`IMDb suggestion fetch failed for ${url}: ${error.message}`);
    return null;
  }
};

module.exports = {
  isImdbUrl,
  parseImdbTarget,
  imdbImageAtWidth,
  imdbDescription,
  pickImdbEntry,
  fetchImdbMetadata
};
