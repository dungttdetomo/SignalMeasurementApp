from flask_sqlalchemy import SQLAlchemy

db = SQLAlchemy()

class WifiSignal(db.Model):
    id = db.Column(db.Integer, primary_key=True)
    latitude = db.Column(db.Float, nullable=False)
    longitude = db.Column(db.Float, nullable=False)
    timestamp = db.Column(db.DateTime, nullable=False)
    signal_strength = db.Column(db.Integer, nullable=False)
