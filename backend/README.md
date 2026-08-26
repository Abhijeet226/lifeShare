# LifeShare MongoDB Backend Service

A lightweight, production-ready REST API backend for the **Life Share** Android application built with **Node.js, Express, Mongoose, JWT authentication, and bcryptjs**.

---

## 🚀 Quick Setup & Run

### 1. Prerequisites
- [Node.js](https://nodejs.org/) (v16+)
- Local [MongoDB Community Server](https://www.mongodb.com/try/download/community) or a free [MongoDB Atlas Cloud Database](https://www.mongodb.com/cloud/atlas).

### 2. Install Dependencies
```bash
cd backend
npm install
```

### 3. Configure Database URL
Copy `.env.example` to `.env`:
```bash
cp .env.example .env
```
Set your MongoDB connection string in `.env` (e.g. for MongoDB Atlas: `mongodb+srv://<username>:<password>@cluster0.mongodb.net/lifeshare?retryWrites=true&w=majority`).

### 4. Start Server
```bash
npm start
```
The server will run at: `http://localhost:5000`

---

## 📡 API Endpoints Overview

| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/api/auth/register` | Register voluntary blood donor with hashed password & JWT |
| `POST` | `/api/auth/login` | Login donor & retrieve JWT auth token |
| `GET` | `/api/donors` | Query donors with `?bloodGroup=O+` & `?city=Mumbai` filters |
| `POST` | `/api/emergency/create` | Broadcast an urgent hospital blood SOS request |
| `GET` | `/api/emergency/list` | Fetch active emergency requests |
| `GET` | `/api/bloodbanks` | Fetch certified blood banks and camps |
| `GET` | `/api/health` | Backend and MongoDB health check |

---

## 📱 Android Client Integration

To connect your Android app to this MongoDB backend:
- On **Android Emulator**: The app connects to your local machine via `http://10.0.2.2:5000/api`.
- On **Physical Android Phone**: Connect your phone to the same Wi-Fi network and use your computer's local IP (e.g. `http://192.168.1.X:5000/api`) or deploy this backend to Render / Railway / Heroku.
