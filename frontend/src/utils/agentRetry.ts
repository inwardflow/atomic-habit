import { HttpAgent } from '@ag-ui/client';

/**
 * Classify an error thrown during agent.runAgent() into a user-friendly
 * category so the UI can show a meaningful message and decide whether to retry.
 */
export type AgentErrorKind =
  | 'network'       // fetch failed, DNS, CORS, offline
  | 'timeout'       // SSE / server timeout
  | 'auth'          // 401 / invalid token
  | 'server'        // 5xx from backend
  | 'agui_protocol' // AG-UI event verification error
  | 'aborted'       // user or code cancelled the request
  | 'unknown';

export interface ClassifiedError {
  kind: AgentErrorKind;
  message: string;
  retryable: boolean;
  original: unknown;
}

export function classifyAgentError(error: unknown): ClassifiedError {
  const msg =
    error instanceof Error ? error.message : typeof error === 'string' ? error : String(error);
  const lower = msg.toLowerCase();

  // Abort
  if (error instanceof DOMException && error.name === 'AbortError') {
    return { kind: 'aborted', message: 'Request was cancelled.', retryable: false, original: error };
  }
  if (lower.includes('abort')) {
    return { kind: 'aborted', message: 'Request was cancelled.', retryable: false, original: error };
  }

  // Network errors (fetch failures)
  if (lower.includes('failed to fetch') || lower.includes('networkerror') || lower.includes('network error') || lower.includes('net::err')) {
    return { kind: 'network', message: 'Network error. Please check your connection.', retryable: true, original: error };
  }

  // Timeout
  if (lower.includes('timeout') || lower.includes('timed out')) {
    return { kind: 'timeout', message: 'Request timed out. The AI may be processing a complex request.', retryable: true, original: error };
  }

  // Auth
  if (lower.includes('401') || lower.includes('unauthorized') || lower.includes('invalid token')) {
    return { kind: 'auth', message: 'Authentication failed. Please log in again.', retryable: false, original: error };
  }

  // Server errors
  if (lower.includes('http 5') || lower.includes('500') || lower.includes('502') || lower.includes('503') || lower.includes('504')) {
    return { kind: 'server', message: 'Server error. Please try again in a moment.', retryable: true, original: error };
  }

  // AG-UI protocol errors (from verifyEvents middleware)
  if (lower.includes('aguierror') || lower.includes('run_started') || lower.includes('run_finished') || lower.includes('text_message_start') || lower.includes('text_message_end') || lower.includes('tool_call_start')) {
    return { kind: 'agui_protocol', message: 'Communication protocol error with AI backend.', retryable: true, original: error };
  }

  // getReader failure
  if (lower.includes('getreader')) {
    return { kind: 'network', message: 'Failed to read response stream.', retryable: true, original: error };
  }

  return { kind: 'unknown', message: msg || 'An unexpected error occurred.', retryable: true, original: error };
}

/**
 * Wrap a promise with a timeout. If the promise doesn't resolve within
 * the given time, it rejects with a timeout error.
 */
function withTimeout<T>(promise: Promise<T>, ms: number, label: string): Promise<T> {
  return new Promise<T>((resolve, reject) => {
    const timer = setTimeout(() => {
      reject(new Error(`Timeout: ${label} did not complete within ${Math.round(ms / 1000)}s`));
    }, ms);

    promise.then(
      (value) => {
        clearTimeout(timer);
        resolve(value);
      },
      (err) => {
        clearTimeout(timer);
        reject(err);
      },
    );
  });
}

/**
 * Run an agent with automatic retry, exponential backoff, and timeout protection.
 *
 * @param agent - The HttpAgent instance
 * @param options - runAgent options (runId, etc.)
 * @param maxRetries - Maximum number of retry attempts (default 2, so up to 3 total attempts)
 * @param baseDelay - Base delay in ms for exponential backoff (default 1500)
 * @param timeoutMs - Timeout per attempt in ms (default 90000 = 90s)
 * @returns The run result, or throws the last ClassifiedError
 */
export async function runAgentWithRetry(
  agent: HttpAgent,
  options: { runId: string },
  maxRetries: number = 2,
  baseDelay: number = 1500,
  timeoutMs: number = 90_000,
): Promise<{ result: unknown }> {
  let lastError: ClassifiedError | null = null;

  for (let attempt = 0; attempt <= maxRetries; attempt++) {
    try {
      // Wrap runAgent with timeout protection
      const result = await withTimeout(
        agent.runAgent(options),
        timeoutMs,
        `Agent run (attempt ${attempt + 1})`,
      );
      return result;
    } catch (error) {
      lastError = classifyAgentError(error);
      console.warn(
        `[AgentRetry] Attempt ${attempt + 1}/${maxRetries + 1} failed:`,
        lastError.kind,
        lastError.message,
      );

      // Don't retry non-retryable errors
      if (!lastError.retryable || attempt === maxRetries) {
        break;
      }

      // Exponential backoff: 1.5s, 3s, 6s...
      const delay = baseDelay * Math.pow(2, attempt);
      await new Promise((resolve) => setTimeout(resolve, delay));

      // Generate a new runId for retry to avoid AG-UI state conflicts
      options = { ...options, runId: `retry-${Date.now()}-${attempt + 1}` };
    }
  }

  throw lastError!;
}

/**
 * Get a user-friendly toast message for a classified error.
 */
export function getErrorToastMessage(error: ClassifiedError): string {
  switch (error.kind) {
    case 'network':
      return 'Network error — check your connection and try again.';
    case 'timeout':
      return 'Request timed out — the AI is taking longer than usual. Try again.';
    case 'auth':
      return 'Session expired — please log in again.';
    case 'server':
      return 'Server error — please try again in a moment.';
    case 'agui_protocol':
      return 'AI communication error — retrying may help.';
    case 'aborted':
      return 'Request cancelled.';
    default:
      return 'Something went wrong. Please try again.';
  }
}
