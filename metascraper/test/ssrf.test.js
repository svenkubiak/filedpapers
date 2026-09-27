'use strict';

const { test, describe, before, after } = require('node:test');
const assert = require('node:assert');
const http = require('node:http');
const net = require('node:net');

const { isAllowedUrl, isBlockedAddress, safeAxios } = require('../ssrf-guard');
const { startGuardedProxy, splitAuthority } = require('../browser-proxy');

const INTERNAL = [
  ['127.0.0.1', 4], ['169.254.169.254', 4], ['10.1.2.3', 4], ['172.16.0.1', 4],
  ['192.168.1.1', 4], ['100.64.0.1', 4], ['0.0.0.0', 4], ['255.255.255.255', 4],
  ['::1', 6], ['fd00::1', 6], ['fe80::1', 6], ['::ffff:127.0.0.1', 6]
];
const PUBLIC = [['8.8.8.8', 4], ['1.1.1.1', 4], ['2606:4700::1111', 6]];

describe('address blocklist', () => {
  INTERNAL.forEach(([ip, family]) => {
    test(`blocks ${ip}`, () => assert.strictEqual(isBlockedAddress(ip, family), true));
  });

  PUBLIC.forEach(([ip, family]) => {
    test(`allows ${ip}`, () => assert.strictEqual(isBlockedAddress(ip, family), false));
  });
});

describe('url pre-flight', () => {
  ['http://example.com/a', 'https://example.com:443/a'].forEach((url) => {
    test(`allows ${url}`, () => assert.strictEqual(isAllowedUrl(url), true));
  });

  [
    'http://example.com:8080/', 'file:///etc/passwd', 'gopher://example.com:70/',
    'http://127.0.0.1/', 'http://169.254.169.254/latest/', 'http://[::1]/',
    'http://[::ffff:127.0.0.1]/', 'not a url'
  ].forEach((url) => {
    test(`rejects ${url}`, () => assert.strictEqual(isAllowedUrl(url), false));
  });
});

describe('guarded http client', () => {
  [
    'http://127.0.0.1:1/', 'http://localhost/', 'http://169.254.169.254/latest/meta-data/',
    'http://[::1]:1/', 'http://192.168.1.1/', 'http://10.0.0.1/'
  ].forEach((url) => {
    test(`refuses to connect to ${url}`, async () => {
      await assert.rejects(
        () => safeAxios.get(url, { timeout: 4000 }),
        (error) => /SSRF guard/.test(error.message),
        `${url} was not blocked by the guard`);
    });
  });
});

describe('browser proxy', () => {
  let proxy;
  let internalPort;
  let internalServer;

  before(async () => {
    internalServer = http.createServer((req, res) => res.end('INTERNAL'));
    await new Promise((resolve) => internalServer.listen(0, '127.0.0.1', resolve));
    internalPort = internalServer.address().port;

    proxy = await startGuardedProxy();
  });

  after(() => {
    proxy.close();
    internalServer.close();
  });

  const request = (url) => new Promise((resolve) => {
    const target = new URL(url);
    const req = http.request({
      host: '127.0.0.1', port: proxy.port, method: 'GET', path: url,
      headers: { Host: target.host }, agent: false
    }, (res) => {
      res.resume();
      resolve(res.statusCode);
    });
    req.on('error', () => resolve(0));
    req.setTimeout(10000, () => { req.destroy(); resolve(0); });
    req.end();
  });

  const connect = (authority) => new Promise((resolve) => {
    const socket = net.connect(proxy.port, '127.0.0.1', () =>
      socket.write(`CONNECT ${authority} HTTP/1.1\r\nHost: ${authority}\r\n\r\n`));

    let buffer = '';
    socket.on('data', (chunk) => {
      buffer += chunk.toString();
      if (buffer.includes('\r\n')) {
        socket.destroy();
        resolve(buffer.split('\r\n')[0]);
      }
    });
    socket.on('error', () => resolve(''));
    socket.setTimeout(10000, () => { socket.destroy(); resolve(''); });
  });

  test('refuses a loopback target the browser would otherwise reach', async () => {
    assert.notStrictEqual(await request(`http://127.0.0.1:${internalPort}/`), 200);
  });

  ['http://169.254.169.254/latest/meta-data/', 'http://192.168.1.1/', 'http://10.0.0.1/', 'http://localhost/']
    .forEach((url) => {
      test(`refuses ${url}`, async () => {
        const status = await request(url);
        assert.ok(status === 403 || status === 502 || status === 0, `unexpected status ${status}`);
      });
    });

  test('refuses a disallowed port', async () => {
    assert.strictEqual(await request('http://example.com:8080/'), 403);
  });

  ['127.0.0.1:443', '169.254.169.254:443', '[::1]:443', 'localhost:443', '10.0.0.1:443']
    .forEach((authority) => {
      test(`refuses CONNECT to ${authority}`, async () => {
        assert.match(await connect(authority), /403/);
      });
    });

  test('refuses CONNECT to a disallowed port', async () => {
    assert.match(await connect('example.com:22'), /403/);
  });
});

describe('authority parsing', () => {
  test('splits host and port', () => assert.deepStrictEqual(splitAuthority('example.com:443'), ['example.com', '443']));
  test('splits bracketed ipv6', () => assert.deepStrictEqual(splitAuthority('[::1]:443'), ['::1', '443']));
  test('handles a missing port', () => assert.deepStrictEqual(splitAuthority('example.com'), ['example.com', null]));
});
