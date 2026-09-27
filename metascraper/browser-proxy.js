'use strict';

// A forward proxy that Chromium is pointed at, so that every request the
// browser makes passes the same target check as the requests this process makes
// itself.
//
// The ssrf guard on the axios client does not help for anything rendered in a
// browser: Chromium resolves and connects on its own. Worse, a blocked axios
// request is what makes /preview fall back to the browser in the first place,
// so without this the guard would push an attacker onto the unguarded path.
//
// Both proxy paths pin the address they validated:
//   - plain http is forwarded through the guarded agent, which validates inside
//     its dns lookup, at connect time
//   - CONNECT resolves the host here and dials the resolved address directly
// There is no window between the check and the connection in either case, so
// dns rebinding does not apply.

const dns = require('dns');
const http = require('http');
const net = require('net');
const { URL } = require('url');
const { guardedLookup, isAllowedUrl, isBlockedAddress, httpAgent, ALLOWED_PORTS } = require('./ssrf-guard');

const PROXY_HOST = '127.0.0.1';
const CONNECT_TIMEOUT_MS = 15000;

const deny = (socket, status, reason) => {
  console.warn(`Browser proxy denied a request: ${reason}`);
  if (socket.writable) {
    socket.write(`HTTP/1.1 ${status}\r\nConnection: close\r\n\r\n`);
  }
  socket.destroy();
};

const handleRequest = (clientRequest, clientResponse) => {
  // A forward proxy receives the absolute url in the request line.
  if (!isAllowedUrl(clientRequest.url)) {
    console.warn('Browser proxy denied a request: disallowed url');
    clientResponse.writeHead(403);
    clientResponse.end();
    return;
  }

  const target = new URL(clientRequest.url);
  const upstream = http.request({
    agent: httpAgent,
    protocol: target.protocol,
    hostname: target.hostname,
    port: target.port || 80,
    method: clientRequest.method,
    path: target.pathname + target.search,
    headers: clientRequest.headers
  }, (upstreamResponse) => {
    clientResponse.writeHead(upstreamResponse.statusCode, upstreamResponse.headers);
    upstreamResponse.pipe(clientResponse);
  });

  upstream.on('error', (error) => {
    // A guard rejection arrives here like any other connection failure.
    console.warn(`Browser proxy upstream error: ${error.message}`);
    if (!clientResponse.headersSent) {
      clientResponse.writeHead(502);
    }
    clientResponse.end();
  });

  clientRequest.pipe(upstream);
};

const handleConnect = (clientRequest, clientSocket, head) => {
  const [host, rawPort] = splitAuthority(clientRequest.url);
  const port = rawPort || '443';

  if (!host || !ALLOWED_PORTS.has(port)) {
    deny(clientSocket, '403 Forbidden', `disallowed CONNECT target ${clientRequest.url}`);
    return;
  }

  const literal = net.isIP(host);
  if (literal) {
    if (isBlockedAddress(host, literal)) {
      deny(clientSocket, '403 Forbidden', `blocked ${host}`);
      return;
    }

    tunnel(clientSocket, head, host, Number(port));
    return;
  }

  // Resolve once, through the guard, and dial exactly what was validated.
  guardedLookup(host, { all: true }, (error, addresses) => {
    if (error || !addresses || addresses.length === 0) {
      deny(clientSocket, '403 Forbidden', error ? error.message : `${host} did not resolve`);
      return;
    }

    tunnel(clientSocket, head, addresses[0].address, Number(port));
  });
};

const tunnel = (clientSocket, head, address, port) => {
  const upstream = net.connect({ host: address, port }, () => {
    clientSocket.write('HTTP/1.1 200 Connection Established\r\n\r\n');
    if (head && head.length) {
      upstream.write(head);
    }

    upstream.pipe(clientSocket);
    clientSocket.pipe(upstream);
  });

  upstream.setTimeout(CONNECT_TIMEOUT_MS, () => upstream.destroy());
  upstream.on('error', () => clientSocket.destroy());
  clientSocket.on('error', () => upstream.destroy());
};

const splitAuthority = (authority) => {
  if (!authority) return [null, null];

  // ipv6 literals arrive bracketed: [::1]:443
  const bracketed = /^\[([^\]]+)\](?::(\d+))?$/.exec(authority);
  if (bracketed) return [bracketed[1], bracketed[2]];

  const index = authority.lastIndexOf(':');
  if (index === -1) return [authority, null];

  return [authority.slice(0, index), authority.slice(index + 1)];
};

/**
 * Starts the proxy on a loopback port.
 *
 * @returns {Promise<{port: number, close: function}>}
 */
const startGuardedProxy = () => {
  return new Promise((resolve, reject) => {
    const server = http.createServer(handleRequest);
    server.on('connect', handleConnect);
    server.on('clientError', (error, socket) => deny(socket, '400 Bad Request', error.message));
    server.once('error', reject);

    // port 0: let the os pick, nothing outside the container can reach it
    server.listen(0, PROXY_HOST, () => {
      const { port } = server.address();
      console.log(`Browser proxy listening on ${PROXY_HOST}:${port}`);
      resolve({ port, close: () => server.close() });
    });
  });
};

module.exports = { startGuardedProxy, splitAuthority };
