/**
 * LIFE SHARE V3 - DONATION HISTORY & DIGITAL CERTIFICATE ROUTES
 */

const express = require('express');
const router = express.Router();
const DonationHistory = require('../models/DonationHistory');
const User = require('../models/User');
const { authenticateToken } = require('../middleware/auth');
const { verifyCertificateIntegrity } = require('../services/certificateService');

// GET /api/donations/my-history - Authenticated Donor's Donation History
router.get('/my-history', authenticateToken, async (req, res) => {
  try {
    const donorId = req.user.id;
    const history = await DonationHistory.find({ donorId })
      .sort({ donationDate: -1 })
      .lean();

    const formattedHistory = history.map((doc) => ({
      id: doc._id,
      certificateId: doc.certificateId,
      hospital: doc.hospital,
      bloodGroup: doc.bloodGroup,
      unitsDonated: doc.unitsDonated || 1,
      donationDate: doc.donationDate,
      status: doc.status || 'VERIFIED',
      verifiedAt: doc.verifiedAt
    }));

    res.json({
      success: true,
      count: formattedHistory.length,
      donations: formattedHistory
    });
  } catch (err) {
    res.status(500).json({ success: false, message: err.message });
  }
});

// GET /api/donations/certificate/:certificateId - Authenticated Certificate Details for Display
router.get('/certificate/:certificateId', authenticateToken, async (req, res) => {
  try {
    const { certificateId } = req.params;
    const donation = await DonationHistory.findOne({ certificateId })
      .populate('donorId', 'name bloodGroup donorId verificationStatus')
      .populate('verifiedBy', 'name role');

    if (!donation) {
      return res.status(404).json({ success: false, message: 'Donation certificate not found' });
    }

    // Security: Only the donor, coordinator, or admin may access full certificate view
    const callerId = req.user.id;
    const caller = await User.findById(callerId);
    const isOwner = donation.donorId && String(donation.donorId._id) === callerId;
    const isPrivileged = caller && (caller.role === 'ADMIN' || caller.role === 'COORDINATOR');

    if (!isOwner && !isPrivileged) {
      return res.status(403).json({
        success: false,
        message: 'Forbidden: You do not have permission to view this certificate.'
      });
    }

    const isValid = verifyCertificateIntegrity(donation);

    res.json({
      success: true,
      certificate: {
        certificateId: donation.certificateId,
        donorName: donation.donorId ? donation.donorId.name : 'Voluntary Donor',
        bloodGroup: donation.bloodGroup,
        unitsDonated: donation.unitsDonated || 1,
        hospital: donation.hospital,
        donationDate: donation.donationDate,
        verifiedAt: donation.verifiedAt,
        status: donation.status,
        attendingDoctor: donation.attendingDoctor || 'Attending Medical Officer',
        doctorRegistrationNo: donation.doctorRegistrationNo || '',
        verifiedBy: donation.verifiedBy ? donation.verifiedBy.name : 'Authorized Hospital Authority',
        certificateHash: donation.certificateHash,
        isTamperProofValid: isValid
      }
    });
  } catch (err) {
    res.status(500).json({ success: false, message: err.message });
  }
});

// GET /api/donations/verify/:certificateId - Public Tamper-Proof Certificate Verification Endpoint
router.get('/verify/:certificateId', async (req, res) => {
  try {
    const { certificateId } = req.params;
    const donation = await DonationHistory.findOne({ certificateId })
      .populate('donorId', 'name bloodGroup verificationStatus');

    if (!donation) {
      return res.status(404).json({
        valid: false,
        message: 'Invalid certificate ID. No verified donation record found.'
      });
    }

    const isIntegrityValid = verifyCertificateIntegrity(donation);

    // Strictly privacy-safe response: NO phone numbers, NO home GPS, NO patient medical records
    res.json({
      valid: isIntegrityValid && donation.status === 'VERIFIED',
      certificateId: donation.certificateId,
      donorName: donation.donorId ? donation.donorId.name : 'Voluntary Donor',
      bloodGroup: donation.bloodGroup,
      unitsDonated: donation.unitsDonated || 1,
      hospital: donation.hospital,
      donationDate: donation.donationDate,
      issuedAt: donation.verifiedAt || donation.createdAt,
      status: donation.status,
      attendingDoctor: donation.attendingDoctor || 'Attending Medical Officer',
      doctorRegistrationNo: donation.doctorRegistrationNo || '',
      verificationAuthority: 'LifeShare Voluntary Blood Network',
      certificateHash: donation.certificateHash
    });
  } catch (err) {
    res.status(500).json({ valid: false, message: err.message });
  }
});

module.exports = router;
