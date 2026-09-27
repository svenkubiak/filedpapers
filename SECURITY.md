# Security Policy

## Supported Versions

| Version | Supported          |
|---|--------------------|
| 4.x.x | :white_check_mark: |
| < 4.0 | :x:                |

## Deployment Requirements

Filed Papers is expected to run behind a reverse proxy. The bundled
`compose.yaml` binds the application to `127.0.0.1` and does not include one,
so the proxy is the operator's responsibility.

### Rate and connection limiting

Password verification uses Argon2id with 80 MB of memory per hash. That is a
deliberate choice for password security, but it makes the authentication
endpoints expensive: a single verification holds roughly 78 MB of heap for its
duration.

The application caps how many of these run in parallel, derived from the
available heap, so it cannot be driven into an out of memory condition. The
proxy should still limit the authentication endpoints, and what matters there
is **concurrency**, not only request rate:

```nginx
limit_req_zone  $binary_remote_addr zone=auth:10m rate=5r/m;
limit_conn_zone $binary_remote_addr zone=authconn:10m;

location ~ ^/(auth/login|auth/mfa|api/v1/users/(login|mfa|refresh))$ {
    limit_req  zone=auth burst=5;   # no nodelay: burst is queued, not admitted at once
    limit_conn authconn 3;
    proxy_pass http://127.0.0.1:9090;
}
```

`limit_req ... nodelay` admits the whole burst simultaneously and is therefore
the wrong tool here. Use `limit_conn`, or omit `nodelay` so the burst is
queued.

### Outbound network access

The application and the metascraper sidecar fetch user supplied URLs. Both
validate that the target resolves to a publicly routable address, but an egress
policy that blocks link local (`169.254.0.0/16`) and private ranges is
recommended as a second line of defense, in particular on cloud instances with
a metadata service. Where available, require the session based variant of that
service (for example IMDSv2 on AWS).

## Reporting a Vulnerability

Please report any security vulnerabilities to sk@svenkubiak.de