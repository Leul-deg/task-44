const LOG_LEVELS = { DEBUG: 0, INFO: 1, WARN: 2, ERROR: 3, NONE: 4 };

const currentLevel = import.meta.env.PROD ? LOG_LEVELS.WARN : LOG_LEVELS.DEBUG;

function shouldLog(level) {
  return LOG_LEVELS[level] >= currentLevel;
}

function formatMessage(level, context, message) {
  const ts = new Date().toISOString();
  return `[${ts}] [${level}] [${context}] ${message}`;
}

const logger = {
  debug(context, message, ...args) {
    if (shouldLog('DEBUG')) console.debug(formatMessage('DEBUG', context, message), ...args);
  },
  info(context, message, ...args) {
    if (shouldLog('INFO')) console.info(formatMessage('INFO', context, message), ...args);
  },
  warn(context, message, ...args) {
    if (shouldLog('WARN')) console.warn(formatMessage('WARN', context, message), ...args);
  },
  error(context, message, ...args) {
    if (shouldLog('ERROR')) console.error(formatMessage('ERROR', context, message), ...args);
  }
};

export default logger;
