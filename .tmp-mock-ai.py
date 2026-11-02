import json, hashlib, math, time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import urlparse, parse_qs

def vec(text: str, dim: int = 1536):
    vals = []
    for i in range(dim):
        h = hashlib.sha256(f"{text}:{i}".encode()).digest()
        vals.append((h[0] / 255.0) - 0.5)
    norm = math.sqrt(sum(v * v for v in vals)) or 1.0
    return [v / norm for v in vals]

class Handler(BaseHTTPRequestHandler):
    protocol_version = 'HTTP/1.1'
    def log_message(self, *args):
        pass

    def _json(self, code, payload):
        body = json.dumps(payload).encode()
        self.send_response(code)
        self.send_header('Content-Type', 'application/json')
        self.send_header('Content-Length', str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def do_GET(self):
        path = urlparse(self.path)
        if path.path == '/v1/models':
            return self._json(200, {'object': 'list', 'data': [
                {'id': 'cognia-demo', 'object': 'model', 'owned_by': 'cognia-local'},
                {'id': 'cognia-embed', 'object': 'model', 'owned_by': 'cognia-local'},
            ]})
        if path.path == '/search':
            q = parse_qs(path.query).get('q', [''])[0]
            return self._json(200, {'query': q, 'results': [
                {'title': f'演示搜索结果：{q}', 'url': 'https://example.com/demo', 'snippet': '这是本地演示搜索服务返回的摘要。'}
            ]})
        return self._json(404, {'error': {'message': 'not found'}})

    def do_POST(self):
        path = urlparse(self.path).path
        length = int(self.headers.get('Content-Length', '0'))
        body = json.loads(self.rfile.read(length) or b'{}')
        if path == '/v1/embeddings':
            inputs = body.get('input')
            if isinstance(inputs, str):
                inputs = [inputs]
            data = [{'object': 'embedding', 'index': i, 'embedding': vec(text)} for i, text in enumerate(inputs or [])]
            return self._json(200, {'object': 'list', 'data': data, 'model': body.get('model')})
        if path != '/v1/chat/completions':
            return self._json(404, {'error': {'message': 'not found'}})
        messages = body.get('messages') or []
        last_user = next((m.get('content', '') for m in reversed(messages) if m.get('role') == 'user'), '')
        tool_msgs = [m for m in messages if m.get('role') == 'tool']
        wants_tool = any(t in last_user for t in ('搜索', 'search', '网页')) and not tool_msgs
        if wants_tool:
            if not body.get('stream'):
                return self._json(200, {
                    'id': 'chatcmpl-demo', 'object': 'chat.completion', 'model': body.get('model'),
                    'choices': [{'index': 0, 'finish_reason': 'tool_calls', 'message': {
                        'role': 'assistant', 'content': None,
                        'tool_calls': [{'id': 'call_demo', 'type': 'function', 'function': {
                            'name': 'web_search', 'arguments': json.dumps({'query': last_user, 'max_results': 3})
                        }}]
                    }}]
                })
        tool_note = ''
        if tool_msgs:
            tool_note = '\n\n我调用了网页搜索工具，结果摘要：\n' + tool_msgs[-1].get('content', '')[:800]
        answer = ('这是 Cognia 本地演示模型的流式回复。\n\n'
                  '你可以先在后台配置真实的 OpenAI 兼容供应商与 Key，'
                  '然后在聊天页选择模型并绑定知识库频道。'
                  + ('\n\n已收到知识库上下文，我会按检索结果回答。' if any(m.get('role') == 'system' and ('参考资料' in (m.get('content') or '') or 'Retrieved knowledge' in (m.get('content') or '')) for m in messages) else '')
                  + tool_note)
        if body.get('stream'):
            self.send_response(200)
            self.send_header('Content-Type', 'text/event-stream')
            self.send_header('Cache-Control', 'no-cache')
            self.send_header('Connection', 'keep-alive')
            self.end_headers()
            def chunk(delta, finish=None):
                payload = {'id': 'chatcmpl-demo', 'object': 'chat.completion.chunk', 'model': body.get('model'),
                           'choices': [{'index': 0, 'delta': delta, 'finish_reason': finish}]}
                self.wfile.write(('data: ' + json.dumps(payload) + '\n\n').encode())
                self.wfile.flush()
            chunk({'role': 'assistant', 'reasoning_content': '用户希望了解 Cognia；先检查上下文，再给出简明回答。'})
            for i in range(0, len(answer), 12):
                chunk({'content': answer[i:i+12]})
                time.sleep(0.01)
            chunk({}, 'stop')
            self.wfile.write(b'data: [DONE]\n\n')
            self.wfile.flush()
            return
        return self._json(200, {
            'id': 'chatcmpl-demo', 'object': 'chat.completion', 'model': body.get('model'),
            'choices': [{'index': 0, 'finish_reason': 'stop', 'message': {'role': 'assistant', 'content': answer}}]
        })

ThreadingHTTPServer(('127.0.0.1', 8099), Handler).serve_forever()
