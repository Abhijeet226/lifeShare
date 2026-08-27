/**
 * LIFE SHARE V3 — PHASE 4 ADMIN OPERATIONS ROUTER
 * Complete Operational Management for Users, Hospitals, Coordinators, Emergencies & Audit Logs.
 */

const express = require('express');
const router = express.Router();
const mongoose = require('mongoose');

const User = require('../models/User');
const Hospital = require('../models/Hospital');
const City = require('../models/City');
const EmergencyRequest = require('../models/EmergencyRequest');
const EmergencyResponse = require('../models/EmergencyResponse');
const DonationHistory = require('../models/DonationHistory');
const AuditLog = require('../models/AuditLog');

const { authenticateToken, requireRole } = require('../middleware/auth');
const { logAuditEvent } = require('../services/auditService');
const { isValidCoordinate } = require('../services/locationService');

// All admin routes strictly require valid JWT and active ADMIN role
router.use(authenticateToken, requireRole('ADMIN'));

// =========================================================================
// 1. DASHBOARD & STATISTICS
// =========================================================================

// GET /api/admin/stats - High-level operational summary
router.get('/stats', async (req, res) => {
  try {
    const [totalUsers, activeDonors, coordinators, hospitals, activeEmergencies, verifiedDonations] = await Promise.all([
      User.countDocuments(),
      User.countDocuments({ role: 'DONOR', isAvailable: true, accountStatus: 'ACTIVE' }),
      User.countDocuments({ role: 'COORDINATOR', accountStatus: 'ACTIVE' }),
      Hospital.countDocuments(),
      EmergencyRequest.countDocuments({ isFulfilled: false, status: { $nin: ['CANCELLED', 'EXPIRED', 'COMPLETED', 'DONATION_COMPLETED', 'FULFILLED'] } }),
      DonationHistory.countDocuments({ status: 'VERIFIED' })
    ]);

    res.json({
      success: true,
      stats: {
        totalUsers,
        activeDonors,
        coordinators,
        hospitals,
        activeEmergencies,
        verifiedDonations
      }
    });
  } catch (err) {
    res.status(500).json({ success: false, message: err.message });
  }
});

// =========================================================================
// 2. USER MANAGEMENT
// =========================================================================

// GET /api/admin/users - Search and list users with pagination and filters
router.get('/users', async (req, res) => {
  try {
    const { role, accountStatus, verificationStatus, search, page = 1, limit = 20 } = req.query;
    const query = {};

    if (role && ['DONOR', 'COORDINATOR', 'ADMIN'].includes(role.toUpperCase())) {
      query.role = role.toUpperCase();
    }
    if (accountStatus && ['ACTIVE', 'SUSPENDED', 'BLOCKED'].includes(accountStatus.toUpperCase())) {
      query.accountStatus = accountStatus.toUpperCase();
    }
    if (verificationStatus && ['UNVERIFIED', 'PHONE_VERIFIED', 'IDENTITY_VERIFIED', 'DONOR_VERIFIED'].includes(verificationStatus.toUpperCase())) {
      query.verificationStatus = verificationStatus.toUpperCase();
    }
    if (search && search.trim()) {
      const regex = new RegExp(search.trim(), 'i');
      query.$or = [{ name: regex }, { email: regex }, { mobile: regex }, { bloodGroup: regex }];
    }

    const pageNum = parseInt(page, 10) || 1;
    const limitNum = Math.min(parseInt(limit, 10) || 20, 100);
    const skip = (pageNum - 1) * limitNum;

    const [total, users] = await Promise.all([
      User.countDocuments(query),
      User.find(query)
        .populate('cityId', 'name stateName')
        .populate('hospitalId', 'name address')
        .sort({ createdAt: -1 })
        .skip(skip)
        .limit(limitNum)
        .select('-password')
        .lean()
    ]);

    const formattedUsers = users.map(u => ({
      id: u._id,
      name: u.name,
      email: u.email,
      mobile: u.mobile,
      bloodGroup: u.bloodGroup,
      role: u.role || 'DONOR',
      accountStatus: u.accountStatus || 'ACTIVE',
      verificationStatus: u.verificationStatus || 'UNVERIFIED',
      city: u.cityId ? u.cityId.name : (u.city || 'Bhubaneswar'),
      cityId: u.cityId ? u.cityId._id : null,
      hospital: u.hospitalId ? u.hospitalId.name : null,
      hospitalId: u.hospitalId ? u.hospitalId._id : null,
      isAvailable: u.isAvailable,
      donationsCount: u.donationsCount || 0,
      createdAt: u.createdAt
    }));

    res.json({
      success: true,
      total,
      page: pageNum,
      totalPages: Math.ceil(total / limitNum),
      users: formattedUsers
    });
  } catch (err) {
    res.status(500).json({ success: false, message: err.message });
  }
});

// GET /api/admin/users/:id - User details
router.get('/users/:id', async (req, res) => {
  try {
    const { id } = req.params;
    if (!mongoose.Types.ObjectId.isValid(id)) {
      return res.status(400).json({ success: false, message: 'Invalid User ID' });
    }

    const user = await User.findById(id)
      .populate('cityId', 'name stateName location')
      .populate('hospitalId', 'name address phone')
      .select('-password');

    if (!user) {
      return res.status(404).json({ success: false, message: 'User not found' });
    }

    const [donations, responses] = await Promise.all([
      DonationHistory.find({ donorId: user._id }).sort({ donationDate: -1 }).limit(10).lean(),
      EmergencyResponse.countDocuments({ donorId: user._id })
    ]);

    res.json({
      success: true,
      user,
      recentDonations: donations,
      totalResponses: responses
    });
  } catch (err) {
    res.status(500).json({ success: false, message: err.message });
  }
});

// PATCH /api/admin/users/:id/status - Update account status (ACTIVE / SUSPENDED / BLOCKED)
router.patch('/users/:id/status', async (req, res) => {
  try {
    const { id } = req.params;
    const { status, reason } = req.body;

    if (!mongoose.Types.ObjectId.isValid(id)) {
      return res.status(400).json({ success: false, message: 'Invalid User ID' });
    }

    if (!['ACTIVE', 'SUSPENDED', 'BLOCKED'].includes(status)) {
      return res.status(400).json({ success: false, message: 'Invalid status. Must be ACTIVE, SUSPENDED, or BLOCKED.' });
    }

    const targetUser = await User.findById(id);
    if (!targetUser) {
      return res.status(404).json({ success: false, message: 'User not found' });
    }

    // Protection against locking out the last active admin
    if (targetUser.role === 'ADMIN' && status !== 'ACTIVE') {
      const activeAdminCount = await User.countDocuments({ role: 'ADMIN', accountStatus: 'ACTIVE' });
      if (activeAdminCount <= 1) {
        return res.status(400).json({
          success: false,
          message: 'Cannot suspend or block the only active administrator in the system.'
        });
      }
    }

    const previousStatus = targetUser.accountStatus;
    targetUser.accountStatus = status;
    await targetUser.save();

    logAuditEvent({
      actorId: req.currentUser._id,
      actorRole: 'ADMIN',
      action: 'USER_STATUS_UPDATED',
      entityType: 'User',
      entityId: targetUser._id,
      metadata: {
        targetUserId: targetUser._id,
        targetUserEmail: targetUser.email,
        previousStatus,
        newStatus: status,
        reason: reason || 'Administrative action'
      }
    });

    res.json({
      success: true,
      message: `User status updated to ${status}`,
      user: {
        id: targetUser._id,
        email: targetUser.email,
        accountStatus: targetUser.accountStatus
      }
    });
  } catch (err) {
    res.status(500).json({ success: false, message: err.message });
  }
});

// PATCH /api/admin/users/:id/role - Update user role (DONOR / COORDINATOR / ADMIN)
router.patch('/users/:id/role', async (req, res) => {
  try {
    const { id } = req.params;
    const { role, hospitalId } = req.body;

    if (!mongoose.Types.ObjectId.isValid(id)) {
      return res.status(400).json({ success: false, message: 'Invalid User ID' });
    }

    if (!['DONOR', 'COORDINATOR', 'ADMIN'].includes(role)) {
      return res.status(400).json({ success: false, message: 'Invalid role. Must be DONOR, COORDINATOR, or ADMIN.' });
    }

    const targetUser = await User.findById(id);
    if (!targetUser) {
      return res.status(404).json({ success: false, message: 'User not found' });
    }

    // Protection against demoting the last active administrator
    if (targetUser.role === 'ADMIN' && role !== 'ADMIN') {
      const activeAdminCount = await User.countDocuments({ role: 'ADMIN', accountStatus: 'ACTIVE' });
      if (activeAdminCount <= 1) {
        return res.status(400).json({
          success: false,
          message: 'Cannot demote the only active administrator in the system.'
        });
      }
    }

    const previousRole = targetUser.role;
    const previousHospitalId = targetUser.hospitalId;

    // Handle role transition
    targetUser.role = role;

    if (role === 'COORDINATOR') {
      if (!hospitalId && !targetUser.hospitalId) {
        return res.status(400).json({
          success: false,
          message: 'Hospital assignment is mandatory when appointing a coordinator.'
        });
      }
      if (hospitalId && mongoose.Types.ObjectId.isValid(hospitalId)) {
        const hospital = await Hospital.findById(hospitalId);
        if (!hospital) {
          return res.status(404).json({ success: false, message: 'Specified hospital not found.' });
        }
        // If reassigning to a different hospital, unlink from previous hospital
        if (previousHospitalId && previousHospitalId.toString() !== hospital._id.toString()) {
          await Hospital.findByIdAndUpdate(previousHospitalId, {
            $pull: { authorizedCoordinatorIds: targetUser._id }
          });
        }
        targetUser.hospitalId = hospital._id;
        // Bi-directional sync
        await Hospital.findByIdAndUpdate(hospital._id, {
          $addToSet: { authorizedCoordinatorIds: targetUser._id }
        });
      }
    } else {
      // If removed from COORDINATOR, clear hospital association
      if (previousHospitalId) {
        await Hospital.findByIdAndUpdate(previousHospitalId, {
          $pull: { authorizedCoordinatorIds: targetUser._id }
        });
      }
      targetUser.hospitalId = null;
    }

    await targetUser.save();

    logAuditEvent({
      actorId: req.currentUser._id,
      actorRole: 'ADMIN',
      action: 'USER_ROLE_UPDATED',
      entityType: 'User',
      entityId: targetUser._id,
      metadata: {
        targetUserId: targetUser._id,
        targetUserEmail: targetUser.email,
        previousRole,
        newRole: role,
        hospitalId: targetUser.hospitalId
      }
    });

    res.json({
      success: true,
      message: `User role updated to ${role}`,
      user: {
        id: targetUser._id,
        email: targetUser.email,
        role: targetUser.role,
        hospitalId: targetUser.hospitalId
      }
    });
  } catch (err) {
    res.status(500).json({ success: false, message: err.message });
  }
});

// PATCH /api/admin/users/:id/verification - Update user verification trust status
router.patch('/users/:id/verification', async (req, res) => {
  try {
    const { id } = req.params;
    const { verificationStatus } = req.body;

    if (!mongoose.Types.ObjectId.isValid(id)) {
      return res.status(400).json({ success: false, message: 'Invalid User ID' });
    }

    if (!['UNVERIFIED', 'PHONE_VERIFIED', 'IDENTITY_VERIFIED', 'DONOR_VERIFIED'].includes(verificationStatus)) {
      return res.status(400).json({ success: false, message: 'Invalid verification status.' });
    }

    const targetUser = await User.findById(id);
    if (!targetUser) {
      return res.status(404).json({ success: false, message: 'User not found' });
    }

    const previousStatus = targetUser.verificationStatus;
    targetUser.verificationStatus = verificationStatus;
    if (verificationStatus !== 'UNVERIFIED' && !targetUser.verifiedAt) {
      targetUser.verifiedAt = new Date();
    }
    await targetUser.save();

    logAuditEvent({
      actorId: req.currentUser._id,
      actorRole: 'ADMIN',
      action: 'USER_VERIFICATION_UPDATED',
      entityType: 'User',
      entityId: targetUser._id,
      metadata: {
        targetUserId: targetUser._id,
        previousStatus,
        newStatus: verificationStatus
      }
    });

    res.json({
      success: true,
      message: `User verification status updated to ${verificationStatus}`,
      user: {
        id: targetUser._id,
        verificationStatus: targetUser.verificationStatus,
        verifiedAt: targetUser.verifiedAt
      }
    });
  } catch (err) {
    res.status(500).json({ success: false, message: err.message });
  }
});

// =========================================================================
// 3. HOSPITAL MANAGEMENT & COORDINATOR ASSIGNMENT
// =========================================================================

// GET /api/admin/hospitals - List all hospitals with full coordinator details
router.get('/hospitals', async (req, res) => {
  try {
    const hospitals = await Hospital.find()
      .populate('cityId', 'name stateName location')
      .populate('authorizedCoordinatorIds', 'name email mobile accountStatus')
      .sort({ name: 1 })
      .lean();

    const formatted = hospitals.map(h => ({
      id: h._id,
      name: h.name,
      address: h.address,
      phone: h.phone || '',
      city: h.cityId ? h.cityId.name : 'Bhubaneswar',
      cityId: h.cityId ? h.cityId._id : null,
      location: h.location,
      verified: !!(h.verified || h.isVerified),
      emergencySupport: h.emergencySupport !== false,
      coordinators: (h.authorizedCoordinatorIds || []).map(c => ({
        id: c._id,
        name: c.name,
        email: c.email,
        mobile: c.mobile,
        accountStatus: c.accountStatus
      })),
      createdAt: h.createdAt
    }));

    res.json({
      success: true,
      count: formatted.length,
      hospitals: formatted
    });
  } catch (err) {
    res.status(500).json({ success: false, message: err.message });
  }
});

// POST /api/admin/hospitals - Create verified hospital with authoritative coordinates
router.post('/hospitals', async (req, res) => {
  try {
    const { name, address, cityId, latitude, longitude, phone, emergencySupport = true, isVerified = true } = req.body;

    if (!name || !name.trim()) {
      return res.status(400).json({ success: false, message: 'Hospital name is required.' });
    }
    if (!address || !address.trim()) {
      return res.status(400).json({ success: false, message: 'Hospital address is required.' });
    }

    const lat = parseFloat(latitude);
    const lng = parseFloat(longitude);
    if (!isValidCoordinate(lng, lat)) {
      return res.status(400).json({ success: false, message: 'Valid latitude and longitude coordinates are required.' });
    }

    let resolvedCityId = cityId;
    if (cityId && !mongoose.Types.ObjectId.isValid(cityId)) {
      return res.status(400).json({ success: false, message: 'Invalid City ID.' });
    } else if (!resolvedCityId) {
      const defaultCity = await City.findOne({ normalizedName: 'bhubaneswar' });
      if (defaultCity) resolvedCityId = defaultCity._id;
    }

    const hospital = new Hospital({
      name: name.trim(),
      address: address.trim(),
      phone: phone ? phone.trim() : '',
      cityId: resolvedCityId,
      location: {
        type: 'Point',
        coordinates: [lng, lat]
      },
      verified: !!isVerified,
      isVerified: !!isVerified,
      emergencySupport: !!emergencySupport,
      authorizedCoordinatorIds: []
    });

    await hospital.save();

    logAuditEvent({
      actorId: req.currentUser._id,
      actorRole: 'ADMIN',
      action: 'HOSPITAL_CREATED',
      entityType: 'Hospital',
      entityId: hospital._id,
      metadata: {
        hospitalName: hospital.name,
        cityId: hospital.cityId,
        coordinates: [lng, lat]
      }
    });

    res.status(201).json({
      success: true,
      message: 'Hospital created successfully.',
      hospital
    });
  } catch (err) {
    res.status(500).json({ success: false, message: err.message });
  }
});

// PATCH /api/admin/hospitals/:id - Update hospital details & coordinates
router.patch('/hospitals/:id', async (req, res) => {
  try {
    const { id } = req.params;
    const { name, address, cityId, latitude, longitude, phone, emergencySupport, isVerified } = req.body;

    if (!mongoose.Types.ObjectId.isValid(id)) {
      return res.status(400).json({ success: false, message: 'Invalid Hospital ID' });
    }

    const hospital = await Hospital.findById(id);
    if (!hospital) {
      return res.status(404).json({ success: false, message: 'Hospital not found.' });
    }

    if (name) hospital.name = name.trim();
    if (address) hospital.address = address.trim();
    if (phone !== undefined) hospital.phone = phone.trim();
    if (emergencySupport !== undefined) hospital.emergencySupport = !!emergencySupport;
    if (isVerified !== undefined) {
      hospital.verified = !!isVerified;
      hospital.isVerified = !!isVerified;
    }
    if (cityId && mongoose.Types.ObjectId.isValid(cityId)) {
      hospital.cityId = cityId;
    }

    if (latitude !== undefined && longitude !== undefined) {
      const lat = parseFloat(latitude);
      const lng = parseFloat(longitude);
      if (isValidCoordinate(lng, lat)) {
        hospital.location = {
          type: 'Point',
          coordinates: [lng, lat]
        };
      }
    }

    await hospital.save();

    logAuditEvent({
      actorId: req.currentUser._id,
      actorRole: 'ADMIN',
      action: 'HOSPITAL_UPDATED',
      entityType: 'Hospital',
      entityId: hospital._id,
      metadata: {
        hospitalName: hospital.name,
        cityId: hospital.cityId
      }
    });

    res.json({
      success: true,
      message: 'Hospital updated successfully.',
      hospital
    });
  } catch (err) {
    res.status(500).json({ success: false, message: err.message });
  }
});

// POST /api/admin/coordinators/onboard - Direct Coordinator Onboarding & Provisioning
router.post('/coordinators/onboard', async (req, res) => {
  try {
    const { name, email, mobile, hospitalId, staffId } = req.body;

    if (!name || !email || !mobile || !hospitalId) {
      return res.status(400).json({ success: false, message: 'Name, email, mobile, and hospitalId are required.' });
    }

    if (!mongoose.Types.ObjectId.isValid(hospitalId)) {
      return res.status(400).json({ success: false, message: 'Invalid Hospital ID.' });
    }

    const hospital = await Hospital.findById(hospitalId);
    if (!hospital) {
      return res.status(404).json({ success: false, message: 'Hospital not found.' });
    }

    const bcrypt = require('bcryptjs');
    // Generate secure temporary 8-character password
    const tempPassword = 'LS@' + Math.floor(100000 + Math.random() * 900000);
    const hashedPassword = await bcrypt.hash(tempPassword, 10);

    const cleanEmail = email.trim().toLowerCase();
    const cleanMobile = mobile.trim();

    let user = await User.findOne({ $or: [{ email: cleanEmail }, { mobile: cleanMobile }] });

    if (user) {
      user.name = name.trim();
      user.role = 'COORDINATOR';
      user.hospitalId = hospital._id;
      user.accountStatus = 'ACTIVE';
      user.password = hashedPassword;
      await user.save();
    } else {
      user = new User({
        name: name.trim(),
        email: cleanEmail,
        mobile: cleanMobile,
        password: hashedPassword,
        bloodGroup: 'O+',
        city: hospital.cityId ? '' : 'Bhubaneswar',
        cityId: hospital.cityId || null,
        role: 'COORDINATOR',
        hospitalId: hospital._id,
        accountStatus: 'ACTIVE',
        verificationStatus: 'VERIFIED'
      });
      await user.save();
    }

    // 1. Add to Hospital active coordinators
    await Hospital.findByIdAndUpdate(hospital._id, {
      $addToSet: { authorizedCoordinatorIds: user._id },
      $push: {
        coordinatorHistory: {
          coordinatorId: user._id,
          name: user.name,
          email: user.email,
          mobile: user.mobile,
          staffId: staffId || '',
          assignedAt: new Date(),
          assignedBy: req.currentUser._id,
          revokedAt: null,
          reason: 'Initial Onboarding',
          donationsVerifiedCount: 0
        }
      }
    });

    // 2. Dispatch simulated / real SMS with temporary credentials
    console.log(`\n=============================================================`);
    console.log(`📱 [COORDINATOR CREDENTIALS DISPATCH]`);
    console.log(`To:          ${user.name} (${user.mobile})`);
    console.log(`Hospital:    ${hospital.name}`);
    console.log(`Email:       ${user.email}`);
    console.log(`Temp Pass:   👉  ${tempPassword}  👈`);
    console.log(`Message:     "Welcome to LifeShare Medical Desk. Your coordinator account has been provisioned. Login with email: ${user.email} and Temp Pass: ${tempPassword}"`);
    console.log(`=============================================================\n`);

    logAuditEvent({
      actorId: req.currentUser._id,
      actorRole: 'ADMIN',
      action: 'COORDINATOR_ONBOARDED',
      entityType: 'Hospital',
      entityId: hospital._id,
      metadata: {
        hospitalId: hospital._id,
        hospitalName: hospital.name,
        coordinatorId: user._id,
        coordinatorEmail: user.email,
        coordinatorMobile: user.mobile,
        staffId: staffId || ''
      }
    });

    res.json({
      success: true,
      message: `Coordinator ${user.name} onboarded successfully! Credentials dispatched to ${user.mobile}.`,
      tempPassword,
      coordinator: {
        id: user._id,
        name: user.name,
        email: user.email,
        mobile: user.mobile,
        role: user.role,
        hospitalId: user.hospitalId,
        hospitalName: hospital.name
      }
    });
  } catch (err) {
    res.status(500).json({ success: false, message: err.message });
  }
});

// GET /api/admin/hospitals/:id/coordinators - List active and historical ex-coordinators
router.get('/hospitals/:id/coordinators', async (req, res) => {
  try {
    const { id } = req.params;
    if (!mongoose.Types.ObjectId.isValid(id)) {
      return res.status(400).json({ success: false, message: 'Invalid Hospital ID.' });
    }

    const hospital = await Hospital.findById(id)
      .populate('authorizedCoordinatorIds', 'name email mobile role accountStatus createdAt')
      .populate('coordinatorHistory.coordinatorId', 'name email mobile role')
      .populate('coordinatorHistory.assignedBy', 'name email')
      .populate('coordinatorHistory.revokedBy', 'name email');

    if (!hospital) {
      return res.status(404).json({ success: false, message: 'Hospital not found.' });
    }

    const activeCoordinators = (hospital.authorizedCoordinatorIds || []).map((c) => ({
      id: c._id,
      name: c.name || 'Coordinator',
      email: c.email,
      mobile: c.mobile,
      role: c.role,
      status: c.accountStatus || 'ACTIVE'
    }));

    const exCoordinators = (hospital.coordinatorHistory || [])
      .filter((h) => h.revokedAt != null)
      .map((h) => ({
        id: h.coordinatorId ? (h.coordinatorId._id || h.coordinatorId) : h._id,
        name: h.name || (h.coordinatorId ? h.coordinatorId.name : 'Ex-Coordinator'),
        email: h.email || (h.coordinatorId ? h.coordinatorId.email : ''),
        mobile: h.mobile || (h.coordinatorId ? h.coordinatorId.mobile : ''),
        staffId: h.staffId || '',
        assignedAt: h.assignedAt,
        revokedAt: h.revokedAt,
        reason: h.reason || 'Unassigned',
        donationsVerifiedCount: h.donationsVerifiedCount || 0
      }));

    res.json({
      success: true,
      hospitalName: hospital.name,
      activeCoordinators,
      exCoordinators
    });
  } catch (err) {
    res.status(500).json({ success: false, message: err.message });
  }
});

// POST /api/admin/hospitals/:id/coordinators/:coordinatorId/unassign - Unassign & archive with history
router.post('/hospitals/:id/coordinators/:coordinatorId/unassign', async (req, res) => {
  try {
    const { id, coordinatorId } = req.params;
    const { reason } = req.body;

    if (!mongoose.Types.ObjectId.isValid(id) || !mongoose.Types.ObjectId.isValid(coordinatorId)) {
      return res.status(400).json({ success: false, message: 'Invalid Hospital or Coordinator ID.' });
    }

    const [hospital, user] = await Promise.all([
      Hospital.findById(id),
      User.findById(coordinatorId)
    ]);

    if (!hospital) return res.status(404).json({ success: false, message: 'Hospital not found.' });

    // Count verified donations by this coordinator for this hospital
    const verifiedCount = await DonationHistory.countDocuments({
      hospitalId: hospital._id,
      verifiedBy: coordinatorId
    });

    // 1. Remove from active authorized coordinators
    await Hospital.findByIdAndUpdate(hospital._id, {
      $pull: { authorizedCoordinatorIds: coordinatorId }
    });

    // 2. Update coordinatorHistory entry with revoked info
    await Hospital.updateOne(
      { _id: hospital._id, 'coordinatorHistory.coordinatorId': coordinatorId, 'coordinatorHistory.revokedAt': null },
      {
        $set: {
          'coordinatorHistory.$.revokedAt': new Date(),
          'coordinatorHistory.$.revokedBy': req.currentUser._id,
          'coordinatorHistory.$.reason': reason || 'Unassigned by Administrator',
          'coordinatorHistory.$.donationsVerifiedCount': verifiedCount
        }
      }
    );

    // 3. Unlink user and revert role to DONOR
    if (user) {
      user.hospitalId = null;
      user.role = 'DONOR';
      await user.save();
    }

    logAuditEvent({
      actorId: req.currentUser._id,
      actorRole: 'ADMIN',
      action: 'COORDINATOR_UNASSIGNED',
      entityType: 'Hospital',
      entityId: hospital._id,
      metadata: {
        hospitalId: hospital._id,
        hospitalName: hospital.name,
        coordinatorId,
        coordinatorName: user ? user.name : '',
        reason: reason || 'Unassigned by Administrator',
        verifiedDonationsTenure: verifiedCount
      }
    });

    res.json({
      success: true,
      message: `Coordinator successfully unassigned from ${hospital.name}. Verification history archived.`,
      verifiedCount
    });
  } catch (err) {
    res.status(500).json({ success: false, message: err.message });
  }
});

// GET /api/admin/coordinators/:id/verifications - Coordinator Verification Audit Trail
router.get('/coordinators/:id/verifications', async (req, res) => {
  try {
    const { id } = req.params;
    if (!mongoose.Types.ObjectId.isValid(id)) {
      return res.status(400).json({ success: false, message: 'Invalid Coordinator ID.' });
    }

    const user = await User.findById(id);
    const verifications = await DonationHistory.find({ verifiedBy: id })
      .populate('donorId', 'name bloodGroup mobile')
      .populate('requestId', 'patientName hospital unitsRequired')
      .sort({ verifiedAt: -1, createdAt: -1 })
      .limit(100);

    res.json({
      success: true,
      coordinator: {
        id: user ? user._id : id,
        name: user ? user.name : 'Coordinator',
        email: user ? user.email : '',
        role: user ? user.role : 'COORDINATOR'
      },
      count: verifications.length,
      verifications: verifications.map((v) => ({
        certificateId: v.certificateId,
        donorName: v.donorId ? v.donorId.name : 'Donor',
        bloodGroup: v.bloodGroup,
        unitsDonated: v.unitsDonated || 1,
        hospital: v.hospitalName || (v.requestId ? v.requestId.hospital : 'Hospital'),
        patientName: v.requestId ? v.requestId.patientName : 'Patient',
        verifiedAt: v.verifiedAt || v.donationDate || v.createdAt
      }))
    });
  } catch (err) {
    res.status(500).json({ success: false, message: err.message });
  }
});

// =========================================================================
// 4. EMERGENCIES & DONATIONS OVERSIGHT
// =========================================================================

// GET /api/admin/emergencies - Overview of all emergencies
router.get('/emergencies', async (req, res) => {
  try {
    const { status, cityId, bloodGroup, page = 1, limit = 20 } = req.query;
    const query = {};

    if (status) query.status = status;
    if (cityId && mongoose.Types.ObjectId.isValid(cityId)) query.cityId = cityId;
    if (bloodGroup) query.bloodGroup = bloodGroup;

    const pageNum = parseInt(page, 10) || 1;
    const limitNum = Math.min(parseInt(limit, 10) || 20, 100);
    const skip = (pageNum - 1) * limitNum;

    const [total, emergencies] = await Promise.all([
      EmergencyRequest.countDocuments(query),
      EmergencyRequest.find(query)
        .populate('requester', 'name email mobile')
        .populate('hospitalId', 'name address')
        .populate('cityId', 'name stateName')
        .sort({ createdAt: -1 })
        .skip(skip)
        .limit(limitNum)
        .lean()
    ]);

    res.json({
      success: true,
      total,
      page: pageNum,
      totalPages: Math.ceil(total / limitNum),
      emergencies
    });
  } catch (err) {
    res.status(500).json({ success: false, message: err.message });
  }
});

// GET /api/admin/donations - Overview of verified donations and certificates
router.get('/donations', async (req, res) => {
  try {
    const { hospitalId, bloodGroup, page = 1, limit = 20 } = req.query;
    const query = {};

    if (hospitalId && mongoose.Types.ObjectId.isValid(hospitalId)) query.hospitalId = hospitalId;
    if (bloodGroup) query.bloodGroup = bloodGroup;

    const pageNum = parseInt(page, 10) || 1;
    const limitNum = Math.min(parseInt(limit, 10) || 20, 100);
    const skip = (pageNum - 1) * limitNum;

    const [total, donations] = await Promise.all([
      DonationHistory.countDocuments(query),
      DonationHistory.find(query)
        .populate('donorId', 'name email mobile bloodGroup donorId')
        .populate('verifiedBy', 'name role email')
        .populate('hospitalId', 'name')
        .sort({ donationDate: -1 })
        .skip(skip)
        .limit(limitNum)
        .lean()
    ]);

    res.json({
      success: true,
      total,
      page: pageNum,
      totalPages: Math.ceil(total / limitNum),
      donations
    });
  } catch (err) {
    res.status(500).json({ success: false, message: err.message });
  }
});

function getAuditActionDisplay(action) {
  if (!action) return 'Security Event';
  const clean = String(action).toUpperCase().trim();
  const map = {
    'EMERGENCY_CREATED': 'Emergency SOS Created',
    'DONORS_NOTIFIED': 'Donors Notified',
    'COORDINATOR_ONBOARDED': 'Coordinator Onboarded',
    'COORDINATOR_ASSIGNED': 'Coordinator Assigned',
    'COORDINATOR_UNASSIGNED': 'Coordinator Unassigned',
    'COORDINATOR_REMOVED': 'Coordinator Removed',
    'DONATION_VERIFIED': 'Blood Donation Verified',
    'USER_STATUS_UPDATED': 'Account Status Changed',
    'USER_ROLE_UPDATED': 'User Role Changed',
    'USER_VERIFICATION_UPDATED': 'Verification Status Updated',
    'HOSPITAL_CREATED': 'Hospital Registered',
    'PASSWORD_RESET': 'Password Reset Triggered',
    'EMERGENCY_CANCELLED': 'Emergency Cancelled',
    'EMERGENCY_FULFILLED': 'Emergency Fulfilled'
  };
  if (map[clean]) return map[clean];
  return clean.replace(/_/g, ' ').toLowerCase().replace(/\b\w/g, l => l.toUpperCase());
}

function getAuditDetailsDisplay(log) {
  if (!log) return '';
  const meta = log.metadata || {};
  const actor = log.actorId ? (log.actorId.name || log.actorId.email || 'Admin') : 'System';

  switch (log.action) {
    case 'COORDINATOR_ONBOARDED':
      return `${actor} onboarded ${meta.coordinatorEmail || meta.coordinatorMobile || 'coordinator'} for ${meta.hospitalName || 'hospital'}`;
    case 'COORDINATOR_ASSIGNED':
      return `${actor} assigned ${meta.coordinatorEmail || 'coordinator'} to ${meta.hospitalName || 'hospital'}`;
    case 'COORDINATOR_UNASSIGNED':
      return `${actor} unassigned ${meta.coordinatorName || 'coordinator'} from ${meta.hospitalName || 'hospital'} (Tenure Archived)`;
    case 'DONATION_VERIFIED':
      return `Verified blood donation certificate issued for donor ${meta.donorName || ''}`;
    case 'EMERGENCY_CREATED':
      return `Emergency SOS for ${meta.bloodGroup || ''} (${meta.unitsRequired || 1} Unit) registered at ${meta.hospital || 'Hospital'}`;
    case 'DONORS_NOTIFIED':
      return `Alert broadcast dispatched to ${meta.notifiedCount || 'eligible nearby'} matching donors`;
    case 'USER_STATUS_UPDATED':
      return `${actor} updated user account status to ${meta.newStatus || 'Active'}`;
    case 'USER_ROLE_UPDATED':
      return `${actor} updated user role to ${meta.newRole || 'Donor'}`;
    case 'HOSPITAL_CREATED':
      return `${actor} registered hospital ${meta.hospitalName || ''}`;
    default:
      return `${actor} performed ${getAuditActionDisplay(log.action)}`;
  }
}

// GET /api/admin/audit-logs - Query paginated operational audit logs
router.get('/audit-logs', async (req, res) => {
  try {
    const { action, actorRole, entityType, page = 1, limit = 50 } = req.query;
    const query = {};

    if (action) query.action = action;
    if (actorRole) query.actorRole = actorRole;
    if (entityType) query.entityType = entityType;

    const pageNum = parseInt(page, 10) || 1;
    const limitNum = Math.min(parseInt(limit, 10) || 50, 100);
    const skip = (pageNum - 1) * limitNum;

    const [total, rawLogs] = await Promise.all([
      AuditLog.countDocuments(query),
      AuditLog.find(query)
        .populate('actorId', 'name email role')
        .sort({ createdAt: -1 })
        .skip(skip)
        .limit(limitNum)
        .lean()
    ]);

    const logs = rawLogs.map((log) => ({
      ...log,
      actionDisplay: getAuditActionDisplay(log.action),
      detailsDisplay: getAuditDetailsDisplay(log)
    }));

    res.json({
      success: true,
      total,
      page: pageNum,
      totalPages: Math.ceil(total / limitNum),
      logs
    });
  } catch (err) {
    res.status(500).json({ success: false, message: err.message });
  }
});

module.exports = router;
