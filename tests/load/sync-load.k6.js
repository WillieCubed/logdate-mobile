import http from "k6/http";
import { check, sleep } from "k6";
import encoding from "k6/encoding";

export const options = {
  vus: 10,
  duration: "30s",
};

const BASE_URL = __ENV.SYNC_BASE_URL || "http://localhost:8080/api/v1";
const TOKEN = __ENV.SYNC_TOKEN;

export default function () {
  if (!TOKEN) {
    throw new Error("SYNC_TOKEN is required");
  }

  const headers = {
    Authorization: `Bearer ${TOKEN}`,
    "Content-Type": "application/json",
  };

  const noteId = `load-note-${__VU}-${__ITER}`;
  const timestamp = Date.now();

  const contentPayload = JSON.stringify({
    id: noteId,
    type: "TEXT",
    content: "load-test",
    mediaUri: null,
    createdAt: timestamp,
    lastUpdated: timestamp,
    deviceId: "load-test",
  });

  let response = http.post(`${BASE_URL}/sync/content`, contentPayload, { headers });
  check(response, { "content upload 200": (r) => r.status === 200 });

  response = http.get(`${BASE_URL}/sync/content/changes?since=0&limit=50`, { headers });
  check(response, { "content changes 200": (r) => r.status === 200 });

  const mediaBytes = encoding.b64encode("load-media", "std");
  const mediaPayload = JSON.stringify({
    contentId: noteId,
    fileName: "load.txt",
    mimeType: "text/plain",
    sizeBytes: 10,
    data: mediaBytes,
    deviceId: "load-test",
  });

  response = http.post(`${BASE_URL}/sync/media`, mediaPayload, { headers });
  check(response, { "media upload 200": (r) => r.status === 200 });

  sleep(1);
}
