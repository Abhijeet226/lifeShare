const mongoose = require('mongoose');

const auditLogSchema = new mongoose.Schema({
  actorId: {
    type: mongoose.Schema.Types.ObjectId,
    ref: 'User',
    default: null,
    index: true
  },
  actorRole: {
    type: String,
    enum: ['DONOR', 'COORDINATOR', 'ADMIN', 'REQUESTER', 'SYSTEM', 'ANONYMOUS'],
    default: 'SYSTEM'
  },
  action: {
    type: String,
    required: true,
    index: true
  },
  entityType: {
    type: String,
    required: true,
    enum: ['User', 'EmergencyRequest', 'EmergencyResponse', 'DonationHistory', 'Hospital', 'City', 'Auth'],
    index: true
  },
  entityId: {
    type: mongoose.Schema.Types.ObjectId,
    default: null,
    index: true
  },
  metadata: {
    type: mongoose.Schema.Types.Mixed,
    default: {}
  },
  createdAt: {
    type: Date,
    default: Date.now,
    index: true
  }
}, {
  timestamps: false,
  versionKey: false
});

// Compound indexes for audit log queries
auditLogSchema.index({ entityType: 1, entityId: 1 });
auditLogSchema.index({ actorId: 1, createdAt: -1 });
auditLogSchema.index({ action: 1, createdAt: -1 });

const AuditLog = mongoose.models.AuditLog || mongoose.model('AuditLog', auditLogSchema);

module.exports = AuditLog;
