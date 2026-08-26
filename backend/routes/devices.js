const express = require('express');
const router = express.Router();
const DeviceToken = require('../models/DeviceToken');
const { authenticateToken } = require('../middleware/auth');

// POST /api/device-tokens - Register or refresh FCM device token
router.post('/', authenticateToken, async (req, res) => {
  try {
    const { token, platform = 'ANDROID' } = req.body;
    if (!token || typeof token !== 'string') {
      return res.status(400).json({ success: false, message: 'Device token is required' });
    }

    const cleanToken = token.trim();

    // Upsert token record for this user
    await DeviceToken.findOneAndUpdate(
      { token: cleanToken },
      {
        userId: req.user.id,
        token: cleanToken,
        platform: platform.toUpperCase()
      },
      { upsert: true, new: true }
    );

    res.json({
      success: true,
      message: 'FCM device token registered successfully'
    });
  } catch (err) {
    res.status(500).json({ success: false, message: err.message });
  }
});

// DELETE /api/device-tokens - Remove token on logout
router.delete('/', authenticateToken, async (req, res) => {
  try {
    const { token } = req.body;
    if (token) {
      await DeviceToken.deleteOne({ token: token.trim(), userId: req.user.id });
    } else {
      await DeviceToken.deleteMany({ userId: req.user.id });
    }

    res.json({
      success: true,
      message: 'Device token unregistered successfully'
    });
  } catch (err) {
    res.status(500).json({ success: false, message: err.message });
  }
});

module.exports = router;
