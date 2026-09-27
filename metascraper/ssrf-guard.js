'use strict';

// Guards all outbound requests against Server-Side Request Forgery.
//
// The check runs inside the agent's DNS lookup, i.e. at connect time. That makes
// it immune to DNS rebinding (there is no window between check and use) and it
// automatically covers every redirect hop, because each hop opens a new socket
// through the same agent.

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
blocklist.addSubnet('100.64.0.0', 10, 'ipv4');      // CGNAT
blocklist.addSubnet('127.0.0.0', 8, 'ipv4');
blocklist.addSubnet('169.254.0.0', 16, 'ipv4');     // link-local / cloud metadata
blocklist.addSubnet('172.16.0.0', 12, 'ipv4');
blocklist.addSubnet('192.0.0.0', 24, 'ipv4');
blocklist.addSubnet('192.0.2.0', 24, 'ipv4');
blocklist.addSubnet('192.168.0.0', 16, 'ipv4');
blocklist.addSubnet('198.18.0.0', 15, 'ipv4');
blocklist.addSubnet('198.51.100.0', 24, 'ipv4');
blocklist.addSubnet('203.0.113.0', 24, 'ipv4');
blocklist.addSubnet('224.0.0.0', 4, 'ipv4');        // multicast
blocklist.addSubnet('240.0.0.0', 4, 'ipv4');        // reserved + broadcast
blocklist.addSubnet('::', 128, 'ipv6');
blocklist.addSubnet('::1', 128, 'ipv6');
blocklist.addSubnet('fc00::', 7, 'ipv6');           // unique local
blocklist.addSubnet('fe80::', 10, 'ipv6');          // link-local
blocklist.addSubnet('ff00::', 8, 'ipv6');           // multicast

const isBlockedAddress = (address, family) => {
  // Normalise IPv4-mapped IPv6 (::ffff:127.0.0.1) so IPv4 rules apply.
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

    // Fail closed: a single internal record is enough to reject the host.
    for (const entry of resolved) {
      if (isBlockedAddress(entry.address, entry.family)) {
        return callback(new Error(`SSRF guard: blocked ${hostname} -> ${entry.address}`));
      }
    }

    if (options && options.all) return callback(null, resolved);

    return callback(null, resolved[0].address, resolved[0].family);
  });
};

// Node skips the lookup hook entirely when the host is already an IP literal,
// so the agent's connect step is guarded as well. Both hooks together form a
// single choke point that also covers redirect hops.
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

/**
 * Cheap pre-flight check on protocol, port and literal address. The
 * authoritative check for anything going through safeAxios happens in the
 * agent; this keeps unwanted targets out of the pipeline early and is the only
 * guard available for targets handed to Chromium.
 */
const isAllowedUrl = (url) => {
  try {
    const parsed = new URL(url);
    if (!ALLOWED_PROTOCOLS.has(parsed.protocol) || !ALLOWED_PORTS.has(parsed.port)) {
      return false;
    }

    // Strip the brackets of an IPv6 literal before checking it.
    const host = parsed.hostname.replace(/^\[|\]$/g, '');
    const family = net.isIP(host);

    return !family || !isBlockedAddress(host, family);
  } catch (e) {
    return false;
  }
};

module.exports = { safeAxios, guardedLookup, isAllowedUrl, isBlockedAddress, httpAgent, httpsAgent, ALLOWED_PORTS };
