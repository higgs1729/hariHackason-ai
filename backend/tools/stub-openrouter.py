# -*- coding: utf-8 -*-
"""
A stand-in for OpenRouter, so the whole pipeline can run without a key.

It answers /api/v1/chat/completions in the documented shape and returns copy
matching whichever schema the request asked for. Everything between the
service layer and the wire is the real code path -- only the provider is
pretend.
"""
import json
import sys
from http.server import BaseHTTPRequestHandler, HTTPServer

PORT = int(sys.argv[1]) if len(sys.argv) > 1 else 8099


def album_answer(body):
    """Reads the photo ids out of the prompt so the reply refers to real rows."""
    text = ""
    for message in body.get("messages", []):
        content = message.get("content")
        if isinstance(content, list):
            for part in content:
                if part.get("type") == "text":
                    text += part.get("text", "")
        elif isinstance(content, str):
            text += content

    ids = []
    for token in text.split("photoId=")[1:]:
        digits = ""
        for ch in token:
            if ch.isdigit():
                digits += ch
            else:
                break
        if digits:
            ids.append(int(digits))

    captions = ["放課後", "夕日", "帰り道", "笑い声", "寄り道", "おやつ",
                "ベンチ", "影ふみ", "自販機", "改札", "坂道", "空"]
    photos = [
        {"photoId": pid,
         "caption": captions[i % len(captions)],
         "place": "梅田" if i % 3 == 0 else None,
         "weather": "晴れ" if i % 2 == 0 else "曇り",
         "comment": "この顔、完全に油断してるでしょ。"}
        for i, pid in enumerate(ids)
    ]
    return {
        "title": "夕方まで笑ってた",
        "coverPhotoId": ids[0] if ids else None,
        "summary": "テスト終わりの放課後、結局どこにも行かずに喋ってた。",
        "photos": photos,
    }


def hint_answer(_body):
    return {
        "hint": "全員で同じ方向を見て、せーので振り向こう",
        "poses": ["振り向きざま", "肩を組んで", "目を閉じて笑う"],
    }


class Handler(BaseHTTPRequestHandler):
    def do_POST(self):
        # Content-Length when there is one, chunked otherwise -- a real
        # provider accepts both, so the stub should not be the strict one.
        if self.headers.get("Transfer-Encoding", "").lower() == "chunked":
            raw = b""
            while True:
                size = int(self.rfile.readline().strip() or b"0", 16)
                if size == 0:
                    self.rfile.readline()
                    break
                raw += self.rfile.read(size)
                self.rfile.readline()
        else:
            raw = self.rfile.read(int(self.headers.get("Content-Length", 0)))
        body = json.loads(raw.decode("utf-8"))

        schema_name = (body.get("response_format", {})
                       .get("json_schema", {})
                       .get("name", ""))
        answer = hint_answer(body) if schema_name == "ShootHint" else album_answer(body)

        envelope = {
            "id": "stub",
            "model": body.get("model"),
            "choices": [{
                "finish_reason": "stop",
                "message": {"role": "assistant",
                            "content": json.dumps(answer, ensure_ascii=False)},
            }],
            "usage": {"prompt_tokens": 0, "completion_tokens": 0},
        }
        out = json.dumps(envelope).encode("utf-8")

        auth = self.headers.get("Authorization", "")
        print("%s schema=%s auth=%s images=%d" % (
            self.path, schema_name or "AlbumDraft", "yes" if auth else "no",
            sum(1 for m in body.get("messages", [])
                if isinstance(m.get("content"), list)
                for p in m["content"] if p.get("type") == "image_url")),
            flush=True)

        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(out)))
        self.end_headers()
        self.wfile.write(out)

    def log_message(self, *args):
        pass


print("stub OpenRouter on :%d" % PORT, flush=True)
HTTPServer(("127.0.0.1", PORT), Handler).serve_forever()
