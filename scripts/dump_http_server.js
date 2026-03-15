const http = require("http");
const { URL } = require("url");
const querystring = require("querystring");

const PORT = process.env.PORT || 1234;
const HOST = "0.0.0.0";

function tryParseJSON(str) {
  try {
    return JSON.parse(str);
  } catch {
    return null;
  }
}

function collectRequestBody(req) {
  return new Promise((resolve, reject) => {
    let chunks = [];

    req.on("data", (chunk) => {
      chunks.push(chunk);
    });

    req.on("end", () => {
      resolve(Buffer.concat(chunks).toString("utf8"));
    });

    req.on("error", reject);
  });
}

function printSection(title, data) {
  console.log(`\n=== ${title} ===`);
  if (typeof data === "string") {
    console.log(data);
  } else {
    console.log(JSON.stringify(data, null, 2));
  }
}

const server = http.createServer(async (req, res) => {
  const requestTime = new Date().toISOString();
  const fullUrl = new URL(req.url, `http://${req.headers.host || `localhost:${PORT}`}`);
  const contentType = req.headers["content-type"] || "";

  console.log("\n\n========================================");
  console.log(`[${requestTime}] ${req.method} ${req.url}`);

  try {
    const rawBody = await collectRequestBody(req);

    let parsedBody = rawBody;

    if (rawBody) {
      if (contentType.includes("application/json")) {
        parsedBody = tryParseJSON(rawBody) ?? rawBody;
      } else if (contentType.includes("application/x-www-form-urlencoded")) {
        parsedBody = querystring.parse(rawBody);
      }
    }

    printSection("Request Line", {
      method: req.method,
      url: req.url,
      path: fullUrl.pathname,
      query: Object.fromEntries(fullUrl.searchParams.entries()),
      ip: req.socket.remoteAddress,
      port: req.socket.remotePort
    });

    printSection("Headers", req.headers);

    printSection("Body (raw)", rawBody || "<empty>");

    printSection("Body (parsed)", parsedBody || "<empty>");

    const responsePayload = {
      ok: true,
      received: {
        method: req.method,
        url: req.url,
        path: fullUrl.pathname,
        query: Object.fromEntries(fullUrl.searchParams.entries()),
        headers: req.headers,
        rawBody: rawBody,
        parsedBody: parsedBody
      },
      timestamp: requestTime
    };

    res.writeHead(200, {
      "Content-Type": "application/json; charset=utf-8"
    });
    res.end(JSON.stringify(responsePayload, null, 2));
  } catch (err) {
    console.error("\n[ERROR]", err);

    res.writeHead(500, {
      "Content-Type": "application/json; charset=utf-8"
    });
    res.end(JSON.stringify({
      ok: false,
      error: err.message
    }, null, 2));
  }
});

server.listen(PORT, HOST, () => {
  console.log(`Dump server listening on http://${HOST}:${PORT}`);
  console.log(`Local access:   http://localhost:${PORT}`);
});
