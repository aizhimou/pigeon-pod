const NEGATIVE_NUMBER_PATTERN = /^-\d+(\.\d+)?$/;

export function quoteTokenIfNeeded(token) {
  if (!/\s/.test(token)) {
    return token;
  }
  return `"${token.replace(/(["\\])/g, '\\$1')}"`;
}

export function tokenizeYtDlpLine(line) {
  if (!line) return [];
  const trimmed = line.trim();
  if (!trimmed) return [];

  // Support simple shell style quotes: --arg "value with spaces"
  const pattern = /"([^"\\]*(?:\\.[^"\\]*)*)"|'([^'\\]*(?:\\.[^'\\]*)*)'|(\S+)/g;
  const tokens = [];
  let match;

  while ((match = pattern.exec(trimmed)) !== null) {
    if (match[1] != null) {
      tokens.push(match[1].replace(/\\(["\\])/g, '$1'));
    } else if (match[2] != null) {
      tokens.push(match[2].replace(/\\(['\\])/g, '$1'));
    } else if (match[3] != null) {
      tokens.push(match[3]);
    }
  }

  if (tokens.length === 0) {
    return trimmed.split(/\s+/).filter(Boolean);
  }
  return tokens;
}

export function formatYtDlpTokens(tokens) {
  if (!tokens || tokens.length === 0) {
    return '';
  }

  const lines = [];
  let currentLine = [];

  tokens.forEach((rawToken) => {
    const token = (rawToken || '').trim();
    if (!token) return;

    const isOption = token.startsWith('-') && !NEGATIVE_NUMBER_PATTERN.test(token);
    if (isOption) {
      if (currentLine.length > 0) {
        lines.push(currentLine.join(' '));
      }
      currentLine = [token];
      return;
    }

    if (currentLine.length === 0) {
      currentLine = [quoteTokenIfNeeded(token)];
    } else {
      currentLine.push(quoteTokenIfNeeded(token));
    }
  });

  if (currentLine.length > 0) {
    lines.push(currentLine.join(' '));
  }

  return lines.join('\n');
}

export function formatYtDlpArgsText(value) {
  if (!value) return '';
  if (Array.isArray(value)) {
    return formatYtDlpTokens(value);
  }
  if (typeof value === 'string') {
    const trimmed = value.trim();
    if (!trimmed) return '';
    try {
      const parsed = JSON.parse(trimmed);
      if (Array.isArray(parsed)) {
        return formatYtDlpTokens(parsed);
      }
    } catch {
      // fallback to keep legacy plain-string values readable
    }
    return formatYtDlpTokens(trimmed.split('\n').flatMap((line) => tokenizeYtDlpLine(line)));
  }
  return '';
}

export function parseYtDlpArgsText(text) {
  if (!text) return [];
  return text
    .split('\n')
    .flatMap((line) => tokenizeYtDlpLine(line))
    .filter(Boolean);
}
