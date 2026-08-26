const jwt = require('jsonwebtoken');

const JWT_SECRET = process.env.JWT_SECRET || 'lifeshare_secure_jwt_secret_2026';

const authenticateToken = (req, res, next) => {
  const authHeader = req.headers['authorization'];
  const token = authHeader && authHeader.startsWith('Bearer ') ? authHeader.substring(7).trim() : null;

  if (!token) {
    return res.status(401).json({
      success: false,
      message: 'Access token required. Please log in.'
    });
  }

  jwt.verify(token, JWT_SECRET, (err, decoded) => {
    if (err) {
      return res.status(401).json({
        success: false,
        message: 'Invalid or expired token. Please log in again.'
      });
    }

    req.user = decoded; // { id, email, ... }
    next();
  });
};

const optionalToken = (req, res, next) => {
  const authHeader = req.headers['authorization'];
  const token = authHeader && authHeader.startsWith('Bearer ') ? authHeader.substring(7).trim() : null;

  if (token) {
    jwt.verify(token, JWT_SECRET, (err, decoded) => {
      if (!err) {
        req.user = decoded;
      }
      next();
    });
  } else {
    next();
  }
};

const User = require('../models/User');

const requireRole = (...allowedRoles) => {
  return async (req, res, next) => {
    try {
      if (!req.user || !req.user.id) {
        return res.status(401).json({
          success: false,
          message: 'Authentication required.'
        });
      }

      const user = await User.findById(req.user.id);
      if (!user) {
        return res.status(404).json({
          success: false,
          message: 'User account not found.'
        });
      }

      // Check account status immediately (real-time revocation)
      if (user.accountStatus === 'SUSPENDED' || user.accountStatus === 'BLOCKED') {
        return res.status(403).json({
          success: false,
          message: `Account is ${user.accountStatus.toLowerCase()}. Access denied.`
        });
      }

      // Role check
      const currentRole = user.role || 'DONOR';
      if (!allowedRoles.includes(currentRole)) {
        return res.status(403).json({
          success: false,
          message: `Forbidden: Access restricted to [${allowedRoles.join(', ')}]. Current role: ${currentRole}`
        });
      }

      req.currentUser = user;
      next();
    } catch (err) {
      console.error('requireRole error:', err);
      res.status(500).json({ success: false, message: 'Authorization error: ' + err.message });
    }
  };
};

module.exports = {
  authenticateToken,
  optionalToken,
  requireRole,
  JWT_SECRET
};
