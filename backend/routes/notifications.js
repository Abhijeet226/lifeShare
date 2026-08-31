const express = require('express');
const router = express.Router();
const mongoose = require('mongoose');
const Notification = require('../models/Notification');
const { authenticateToken } = require('../middleware/auth');

// GET /api/notifications - List user's persistent notifications with channel filters & unread counts
router.get('/', authenticateToken, async (req, res) => {
  try {
    const userId = req.user.id;
    const { channel, limit = 50, page = 1 } = req.query;

    const query = {
      userId,
      isDeleted: { $ne: true }
    };

    if (channel && channel !== 'ALL') {
      query.channel = channel.toUpperCase();
    }

    const skip = (parseInt(page, 10) - 1) * parseInt(limit, 10);
    const notifications = await Notification.find(query)
      .sort({ updatedAt: -1, createdAt: -1 })
      .skip(skip)
      .limit(parseInt(limit, 10));

    const totalCount = await Notification.countDocuments(query);
    const totalUnreadCount = await Notification.countDocuments({
      userId,
      isDeleted: { $ne: true },
      isRead: false
    });

    // Unread count per channel
    const channelCounts = await Notification.aggregate([
      {
        $match: {
          userId: new mongoose.Types.ObjectId(userId),
          isDeleted: { $ne: true },
          isRead: false
        }
      },
      {
        $group: {
          _id: '$channel',
          count: { $sum: 1 }
        }
      }
    ]);

    const channelUnread = {
      EMERGENCY: 0,
      CHAT: 0,
      CERTIFICATES: 0,
      UPDATES: 0
    };
    channelCounts.forEach((c) => {
      if (c._id && channelUnread.hasOwnProperty(c._id)) {
        channelUnread[c._id] = c.count;
      }
    });

    res.json({
      success: true,
      totalCount,
      unreadCount: totalUnreadCount,
      channelUnread,
      notifications
    });
  } catch (err) {
    console.error('Error fetching notifications:', err);
    res.status(500).json({ success: false, message: err.message });
  }
});

// PATCH /api/notifications/:id/read - Mark single notification as read
router.patch('/:id/read', authenticateToken, async (req, res) => {
  try {
    const { id } = req.params;
    if (!mongoose.Types.ObjectId.isValid(id)) {
      return res.status(400).json({ success: false, message: 'Invalid notification ID' });
    }

    const notification = await Notification.findOneAndUpdate(
      { _id: id, userId: req.user.id },
      { isRead: true },
      { new: true }
    );

    if (!notification) {
      return res.status(404).json({ success: false, message: 'Notification not found' });
    }

    const unreadCount = await Notification.countDocuments({
      userId: req.user.id,
      isDeleted: { $ne: true },
      isRead: false
    });

    res.json({
      success: true,
      notification,
      unreadCount
    });
  } catch (err) {
    res.status(500).json({ success: false, message: err.message });
  }
});

// PATCH /api/notifications/mark-all-read - Mark all notifications as read (optionally for a channel)
router.patch('/mark-all-read', authenticateToken, async (req, res) => {
  try {
    const userId = req.user.id;
    const { channel } = req.body || {};

    const query = {
      userId,
      isDeleted: { $ne: true },
      isRead: false
    };

    if (channel && channel !== 'ALL') {
      query.channel = channel.toUpperCase();
    }

    await Notification.updateMany(query, { isRead: true });

    const totalUnread = await Notification.countDocuments({
      userId,
      isDeleted: { $ne: true },
      isRead: false
    });

    res.json({
      success: true,
      message: 'All notifications marked as read',
      unreadCount: totalUnread
    });
  } catch (err) {
    res.status(500).json({ success: false, message: err.message });
  }
});

// DELETE /api/notifications/:id - Delete single notification
router.delete('/:id', authenticateToken, async (req, res) => {
  try {
    const { id } = req.params;
    if (!mongoose.Types.ObjectId.isValid(id)) {
      return res.status(400).json({ success: false, message: 'Invalid notification ID' });
    }

    const notification = await Notification.findOneAndUpdate(
      { _id: id, userId: req.user.id },
      { isDeleted: true },
      { new: true }
    );

    if (!notification) {
      return res.status(404).json({ success: false, message: 'Notification not found' });
    }

    const unreadCount = await Notification.countDocuments({
      userId: req.user.id,
      isDeleted: { $ne: true },
      isRead: false
    });

    res.json({
      success: true,
      message: 'Notification deleted',
      unreadCount
    });
  } catch (err) {
    res.status(500).json({ success: false, message: err.message });
  }
});

// DELETE /api/notifications/clear-all - Clear all notifications for user
router.delete('/clear-all', authenticateToken, async (req, res) => {
  try {
    const userId = req.user.id;
    const { channel } = req.body || {};

    const query = {
      userId,
      isDeleted: { $ne: true }
    };

    if (channel && channel !== 'ALL') {
      query.channel = channel.toUpperCase();
    }

    await Notification.updateMany(query, { isDeleted: true });

    res.json({
      success: true,
      message: 'All notifications cleared',
      unreadCount: 0
    });
  } catch (err) {
    res.status(500).json({ success: false, message: err.message });
  }
});

module.exports = router;
