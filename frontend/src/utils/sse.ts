/**
 * Minimal Server-Sent Events client built on fetch.
 *
 * The browser's EventSource cannot send an Authorization header, which forces the token into the
 * URL (and from there into access and proxy logs). This reader sends it as a header instead.
 */
export interface SseOptions {
  headers?: Record<string, string>;
  signal: AbortSignal;
  onEvent: (event: string, data: string) => void;
}

export class SseHttpError extends Error {
  readonly status: number;

  constructor(status: number) {
    super(`SSE request failed with HTTP ${status}`);
    this.status = status;
  }
}

/** Resolves when the server closes the stream; rejects on network/HTTP errors or abort. */
export async function readSse(url: string, { headers, signal, onEvent }: SseOptions): Promise<void> {
  const response = await fetch(url, {
    headers: { Accept: 'text/event-stream', ...headers },
    credentials: 'include',
    signal,
  });
  if (!response.ok || !response.body) {
    throw new SseHttpError(response.status);
  }

  const reader = response.body.pipeThrough(new TextDecoderStream()).getReader();
  let buffer = '';
  for (;;) {
    const { value, done } = await reader.read();
    if (done) return;
    buffer += value.replace(/\r\n/g, '\n');

    let boundary = buffer.indexOf('\n\n');
    while (boundary !== -1) {
      dispatch(buffer.slice(0, boundary), onEvent);
      buffer = buffer.slice(boundary + 2);
      boundary = buffer.indexOf('\n\n');
    }
  }
}

function dispatch(block: string, onEvent: SseOptions['onEvent']) {
  let event = 'message';
  const data: string[] = [];
  for (const line of block.split('\n')) {
    if (line.startsWith(':')) continue; // comment / keep-alive
    const colon = line.indexOf(':');
    const field = colon === -1 ? line : line.slice(0, colon);
    const value = colon === -1 ? '' : line.slice(colon + 1).replace(/^ /, '');
    if (field === 'event') event = value;
    else if (field === 'data') data.push(value);
  }
  if (data.length > 0) onEvent(event, data.join('\n'));
}
