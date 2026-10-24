'use strict';

const { describe, test } = require('node:test');
const assert = require('node:assert');

const { parseImdbTarget, imdbImageAtWidth, imdbDescription, pickImdbEntry, isImdbUrl } = require('../imdb');

const POSTER = 'https://m.media-amazon.com/images/M/MV5Bexample@._V1_.jpg';

describe('imdb urls', () => {
  test('reads a title id from the canonical path', () => {
    const target = parseImdbTarget('https://www.imdb.com/title/tt0111161/');
    assert.strictEqual(target.id, 'tt0111161');
    assert.strictEqual(target.apiUrl, 'https://v3.sg.media-imdb.com/suggestion/t/tt0111161.json');
  });

  test('reads a title id from localized and mobile paths', () => {
    assert.strictEqual(parseImdbTarget('https://www.imdb.com/de/title/tt0111161/?ref_=nv').id, 'tt0111161');
    assert.strictEqual(parseImdbTarget('https://m.imdb.com/title/tt0111161/reviews/').id, 'tt0111161');
  });

  test('reads a name id', () => {
    const target = parseImdbTarget('https://www.imdb.com/name/nm0000151/');
    assert.strictEqual(target.id, 'nm0000151');
    assert.strictEqual(target.apiUrl, 'https://v3.sg.media-imdb.com/suggestion/n/nm0000151.json');
  });

  test('ignores pages that are not a title or a name', () => {
    assert.strictEqual(parseImdbTarget('https://www.imdb.com/chart/top/'), null);
    assert.strictEqual(parseImdbTarget('https://example.com/title/tt0111161/'), null);
    assert.strictEqual(isImdbUrl('https://www.imdb.com/chart/top/'), true);
  });
});

describe('imdb posters', () => {
  test('caps the poster width so it stays within the image limits', () => {
    assert.strictEqual(
      imdbImageAtWidth(POSTER, 600),
      'https://m.media-amazon.com/images/M/MV5Bexample@._V1_SX600_.jpg'
    );
  });

  test('replaces an existing size modifier', () => {
    assert.strictEqual(
      imdbImageAtWidth(POSTER.replace('._V1_.jpg', '._V1_SX2000_.jpg'), 400),
      'https://m.media-amazon.com/images/M/MV5Bexample@._V1_SX400_.jpg'
    );
  });
});

describe('imdb suggestion payload', () => {
  const payload = {
    d: [
      { id: 'tt9999999', l: 'Wrong match', y: 2001, s: 'Nobody' },
      {
        id: 'tt0111161',
        l: 'The Shawshank Redemption',
        y: 1994,
        s: 'Tim Robbins, Morgan Freeman',
        i: { imageUrl: POSTER }
      }
    ]
  };

  test('picks the entry with the exact id, not the first hit', () => {
    assert.strictEqual(pickImdbEntry(payload, 'tt0111161').l, 'The Shawshank Redemption');
    assert.strictEqual(pickImdbEntry(payload, 'tt0000000'), null);
  });

  test('builds a description from year and cast', () => {
    assert.strictEqual(
      imdbDescription(pickImdbEntry(payload, 'tt0111161')),
      '1994 · Tim Robbins, Morgan Freeman'
    );
  });
});
