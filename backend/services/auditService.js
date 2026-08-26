const AuditLog = require('../models/AuditLog');

/**
 * Strips sensitive/confidential keys from metadata object
 */
function sanitizeMetadata(metadata) {
  if (!metadata || typeof metadata !== 'object') return {};
  const sanitized = { ...metadata };
  const sensitiveKeys = [
    'password',
    'passwords',
    'otp',
    'token',
    'accessToken',
    'refreshToken',
    'jwt',
    'auth',
    'authorization',
    'coordinates',
    'latitude',
    'longitude',
    'location',
    'requestLocation',
    'hospitalLocation',
    'pin'
  ];

  for (const key of Object.keys(sanitized)) {
    if (sensitiveKeys.includes(key.toLowerCase())) {
      delete sanitized[key];
    } else if (typeof sanitized[key] === 'object' && sanitized[key] !== null) {
      sanitized[key] = sanitizeMetadata(sanitized[key]);
    }
  }
  return sanitized;
}

/**
 * Asynchronously logs security and state transitions.
 * Guaranteed never to throw or break the primary business transaction.
 */
async function logAuditEvent({ actorId = null, actorRole = 'SYSTEM', action, entityType, entityId = null, metadata = {} }) {
  try {
    if (!action || !entityType) return;
    const cleanMeta = sanitizeMetadata(metadata);
    await AuditLog.create({
      actorId,
      actorRole,
      action,
      entityType,
      entityId,
      metadata: cleanMeta,
      createdAt: new Date()
    });
  } catch (err) {
    // Non-blocking: never crash the parent workflow if audit logging fails
    console.error('⚠️ [AUDIT LOG ERROR - NON-BLOCKING]:', err.message);
  }
}

module.exports = {
  logAuditEvent,
  sanitizeMetadata
};
