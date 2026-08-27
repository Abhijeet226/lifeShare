/**
 * Digital Blood Donation Certificate & Cryptographic Integrity Service
 */

const crypto = require('crypto');

const CERT_SECRET = process.env.CERT_SECRET || process.env.JWT_SECRET || 'lifeshare_secure_certificate_secret_2026';

/**
 * Generate unique certificate ID and cryptographic hash for a verified blood donation.
 */
function generateDonationCertificate({ donorId, bloodGroup, hospitalName, donationDate, verifiedById, attendingDoctor, doctorRegistrationNo }) {
  const timestamp = Date.now().toString(36).toUpperCase();
  const randomSuffix = crypto.randomBytes(3).toString('hex').toUpperCase();
  const certificateId = `CERT-LS-${timestamp}-${randomSuffix}`;

  const payload = JSON.stringify({
    certificateId,
    donorId: donorId.toString(),
    bloodGroup,
    hospitalName,
    donationDate: new Date(donationDate).toISOString(),
    verifiedById: verifiedById.toString(),
    attendingDoctor: attendingDoctor || 'Attending Medical Officer',
    doctorRegistrationNo: doctorRegistrationNo || ''
  });

  const certificateHash = crypto
    .createHmac('sha256', CERT_SECRET)
    .update(payload)
    .digest('hex');

  return {
    certificateId,
    certificateHash,
    issuedAt: new Date()
  };
}

/**
 * Validate a certificate's integrity against its authoritative database record.
 */
function verifyCertificateIntegrity(donationHistory) {
  if (!donationHistory || !donationHistory.certificateId || !donationHistory.certificateHash) {
    return false;
  }

  const donorId = donationHistory.donorId
    ? (donationHistory.donorId._id || donationHistory.donorId).toString()
    : '';

  const verifiedById = donationHistory.verifiedBy
    ? (donationHistory.verifiedBy._id || donationHistory.verifiedBy).toString()
    : '';

  // 1. Try modern payload containing doctor metadata
  const payloadWithDoctor = JSON.stringify({
    certificateId: donationHistory.certificateId,
    donorId,
    bloodGroup: donationHistory.bloodGroup,
    hospitalName: donationHistory.hospital,
    donationDate: new Date(donationHistory.donationDate).toISOString(),
    verifiedById,
    attendingDoctor: donationHistory.attendingDoctor || 'Attending Medical Officer',
    doctorRegistrationNo: donationHistory.doctorRegistrationNo || ''
  });

  const expectedHashWithDoctor = crypto
    .createHmac('sha256', CERT_SECRET)
    .update(payloadWithDoctor)
    .digest('hex');

  if (donationHistory.certificateHash === expectedHashWithDoctor) {
    return true;
  }

  // 2. Fallback check for legacy certificates without doctor field
  const legacyPayload = JSON.stringify({
    certificateId: donationHistory.certificateId,
    donorId,
    bloodGroup: donationHistory.bloodGroup,
    hospitalName: donationHistory.hospital,
    donationDate: new Date(donationHistory.donationDate).toISOString(),
    verifiedById
  });

  const legacyHash = crypto
    .createHmac('sha256', CERT_SECRET)
    .update(legacyPayload)
    .digest('hex');

  return donationHistory.certificateHash === legacyHash;
}

module.exports = {
  generateDonationCertificate,
  verifyCertificateIntegrity
};
