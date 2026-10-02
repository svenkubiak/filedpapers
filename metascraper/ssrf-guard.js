'use strict';

// The SSRF check runs in the agents' DNS lookup at connect time: no DNS rebinding window, and every redirect hop is re-checked.

const axios = require('axios');
const dns = require('dns');
const http = require('http');
const https = require('https');
const net = require('net');
const { URL } = require('url');

const ALLOWED_PROTOCOLS = new Set(['http:', 'https:']);
const ALLOWED_PORTS = new Set(['', '80', '443']);

const blocklist = new net.BlockList();
blocklist.addSubnet('0.0.0.0', 8, 'ipv4');
blocklist.addSubnet('10.0.0.0', 8, 'ipv4');
blocklist.addSubnet('100.64.0.0', 10, 'ipv4');
blocklist.addSubnet('127.0.0.0', 8, 'ipv4');
blocklist.addSubnet('169.254.0.0', 16, 'ipv4');
blocklist.addSubnet('172.16.0.0', 12, 'ipv4');
blocklist.addSubnet('192.0.0.0', 24, 'ipv4');
blocklist.addSubnet('192.0.2.0', 24, 'ipv4');
blocklist.addSubnet('192.168.0.0', 16, 'ipv4');
blocklist.addSubnet('198.18.0.0', 15, 'ipv4');
blocklist.addSubnet('198.51.100.0', 24, 'ipv4');
blocklist.addSubnet('203.0.113.0', 24, 'ipv4');
blocklist.addSubnet('224.0.0.0', 4, 'ipv4');
blocklist.addSubnet('240.0.0.0', 4, 'ipv4');
blocklist.addSubnet('::', 128, 'ipv6');
blocklist.addSubnet('::1', 128, 'ipv6');
blocklist.addSubnet('fc00::', 7, 'ipv6');
blocklist.addSubnet('fe80::', 10, 'ipv6');
blocklist.addSubnet('ff00::', 8, 'ipv6');

const isBlockedAddress = (address, family) => {
  // IPv4-mapped IPv6 (::ffff:127.0.0.1) must hit the IPv4 rules.
  const mapped = /^::ffff:(\d+\.\d+\.\d+\.\d+)$/i.exec(address);
  if (mapped) {
    return blocklist.check(mapped[1], 'ipv4');
  }

  return blocklist.check(address, family === 6 ? 'ipv6' : 'ipv4');
};

const guardedLookup = (hostname, options, callback) => {
  if (typeof options === 'function') {
    callback = options;
    options = {};
  }

  dns.lookup(hostname, { ...options, all: true }, (error, addresses) => {
    if (error) return callback(error);

    const resolved = Array.isArray(addresses) ? addresses : [addresses];
    if (resolved.length === 0) {
      return callback(new Error(`SSRF guard: ${hostname} did not resolve`));
    }

    // Fail closed: one internal record rejects the host.
    for (const entry of resolved) {
      if (isBlockedAddress(entry.address, entry.family)) {
        return callback(new Error(`SSRF guard: blocked ${hostname} -> ${entry.address}`));
      }
    }

    if (options && options.all) return callback(null, resolved);

    return callback(null, resolved[0].address, resolved[0].family);
  });
};

// Node skips the lookup hook for IP literals, so the connect step is guarded too.
const guardAgent = (agent) => {
  const createConnection = agent.createConnection.bind(agent);

  agent.createConnection = (options, callback) => {
    const host = options.host;
    const family = host ? net.isIP(host) : 0;

    if (family && isBlockedAddress(host, family)) {
      const error = new Error(`SSRF guard: blocked ${host}`);
      if (typeof callback === 'function') {
        process.nextTick(callback, error);
        return undefined;
      }
      throw error;
    }

    return createConnection(options, callback);
  };

  return agent;
};

const httpAgent = guardAgent(new http.Agent({ keepAlive: false, lookup: guardedLookup }));
const httpsAgent = guardAgent(new https.Agent({ keepAlive: false, lookup: guardedLookup }));

const safeAxios = axios.create({ httpAgent, httpsAgent });

// Pre-flight only (no DNS); the authoritative check is in the agents' lookup.
const isAllowedUrl = (url) => {
  try {
    const parsed = new URL(url);
    if (!ALLOWED_PROTOCOLS.has(parsed.protocol) || !ALLOWED_PORTS.has(parsed.port)) {
      return false;
    }

    const host = parsed.hostname.replace(/^\[|\]$/g, '');
    const family = net.isIP(host);

    return !family || !isBlockedAddress(host, family);
  } catch (e) {
    return false;
  }
};

module.exports = { safeAxios, guardedLookup, isAllowedUrl, isBlockedAddress, httpAgent, httpsAgent, ALLOWED_PORTS };
