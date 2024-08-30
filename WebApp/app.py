from fastapi import FastAPI, HTTPException, Request, Form, Depends, status
from fastapi.responses import HTMLResponse, FileResponse, RedirectResponse, JSONResponse
from fastapi.templating import Jinja2Templates
from fastapi.staticfiles import StaticFiles
from fastapi.security import HTTPBasic, HTTPBasicCredentials
from datetime import datetime, timedelta
import random
import folium
from folium.plugins import MarkerCluster
import csv
import os
from pydantic import BaseModel
from typing import List, Optional

app = FastAPI()
templates = Jinja2Templates(directory="templates")


CSV_FILE = "wifi_signals.csv"
USERS_FILE = "users.csv"
security = HTTPBasic()

class User(BaseModel):
    username: str
    email: str
    password: str
    is_admin: bool = False
    last_login: Optional[datetime] = None
    is_active: bool = True

users = []

def load_users():
    global users
    if os.path.exists(USERS_FILE):
        with open(USERS_FILE, mode='r') as file:
            reader = csv.reader(file)
            next(reader)  # Skip header
            users = []
            for row in reader:
                user = User(
                    username=row[0],
                    email=row[1],
                    password=row[2],
                    is_admin=row[3] == 'True',
                    last_login=datetime.fromisoformat(row[4]) if row[4] else None,
                    is_active=row[5] == 'True'
                )
                users.append(user)

def save_users():
    with open(USERS_FILE, mode='w', newline='') as file:
        writer = csv.writer(file)
        writer.writerow(['username', 'email', 'password', 'is_admin', 'last_login', 'is_active'])
        for user in users:
            writer.writerow([
                user.username,
                user.email,
                user.password,
                user.is_admin,
                user.last_login.isoformat() if user.last_login else '',
                user.is_active
            ])

load_users()

def get_current_user(request: Request) -> Optional[User]:
    username = request.cookies.get("username")
    if username:
        return next((u for u in users if u.username == username), None)
    return None

def login_required(request: Request):
    username = request.cookies.get("username")
    if not username:
        return RedirectResponse(url="/login", status_code=status.HTTP_302_FOUND)
    user = next((u for u in users if u.username == username), None)
    if not user:
        return RedirectResponse(url="/login", status_code=status.HTTP_302_FOUND)
    return user

@app.get("/login", response_class=HTMLResponse)
async def login_page(request: Request):
    return templates.TemplateResponse("login.html", {"request": request})

@app.post("/login")
async def login(request: Request, username: str = Form(...), password: str = Form(...)):
    user = next((u for u in users if u.username == username and u.password == password), None)
    if user and user.is_active:
        user.last_login = datetime.now()
        save_users()
        response = RedirectResponse(url="/", status_code=status.HTTP_302_FOUND)
        response.set_cookie(key="username", value=username, httponly=True)
        return response
    return templates.TemplateResponse("login.html", {"request": request, "error": "Invalid credentials or inactive account"})

@app.get("/logout")
async def logout(response: JSONResponse):
    response = RedirectResponse(url="/login", status_code=status.HTTP_302_FOUND)
    response.delete_cookie("username")
    return response

@app.get("/register", response_class=HTMLResponse)
async def register_page(request: Request):
    return templates.TemplateResponse("register.html", {"request": request})

@app.post("/register")
async def register(username: str = Form(...), email: str = Form(...), password: str = Form(...)):
    if any(u.username == username for u in users):
        return RedirectResponse(url="/register?error=1", status_code=status.HTTP_302_FOUND)
    new_user = User(username=username, email=email, password=password)
    users.append(new_user)
    save_users()
    return RedirectResponse(url="/login", status_code=status.HTTP_302_FOUND)

@app.get("/logout")
async def logout(request: Request):
    response = RedirectResponse(url="/login", status_code=status.HTTP_302_FOUND)
    response.delete_cookie("username")
    return response

@app.get("/admin", response_class=HTMLResponse)
async def admin_page(request: Request):
    current_user = login_required(request)
    if isinstance(current_user, RedirectResponse):
        return current_user
    if not current_user.is_admin:
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Not authorized")
    return templates.TemplateResponse("admin.html", {"request": request, "users": users})

@app.post("/admin/delete/{username}")
async def delete_user(request: Request, username: str):
    current_user = login_required(request)
    if isinstance(current_user, RedirectResponse):
        return current_user
    if not current_user.is_admin:
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Not authorized")
    global users
    users = [u for u in users if u.username != username]
    save_users()
    return RedirectResponse(url="/admin", status_code=status.HTTP_302_FOUND)

@app.post("/admin/edit/{username}")
async def edit_user(request: Request, username: str, new_username: str = Form(...), email: str = Form(...), is_admin: bool = Form(False), is_active: bool = Form(False)):
    current_user = login_required(request)
    if isinstance(current_user, RedirectResponse):
        return current_user
    if not current_user.is_admin:
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Not authorized")
    user = next((u for u in users if u.username == username), None)
    if user:
        # Check if the new username already exists
        if new_username != username and any(u.username == new_username for u in users):
            raise HTTPException(status_code=400, detail="Username already exists")
        user.username = new_username
        user.email = email
        user.is_admin = is_admin
        user.is_active = is_active
        save_users()
    return RedirectResponse(url="/admin", status_code=status.HTTP_302_FOUND)

@app.post("/api/generate-data")
def generate_data():
    base_latitude = 35.6837
    base_longitude = 139.6805
    start_date = datetime(2024, 8, 1)
    end_date = datetime(2024, 8, 7)
    delta = end_date - start_date

    with open(CSV_FILE, mode='w', newline='') as file:
        writer = csv.writer(file)
        writer.writerow(['latitude', 'longitude', 'timestamp', 'signal_strength'])

        for _ in range(100):
            random_days = random.randint(0, delta.days)
            random_seconds = random.randint(0, 86400)
            random_time = start_date + timedelta(days=random_days, seconds=random_seconds)

            random_latitude = base_latitude + random.uniform(-0.01, 0.01)
            random_longitude = base_longitude + random.uniform(-0.01, 0.01)
            random_signal_strength = random.randint(0, 4)

            writer.writerow([
                random_latitude,
                random_longitude,
                random_time.strftime('%Y-%m-%d %H:%M:%S'),
                random_signal_strength
            ])

    return {"message": "Demo data generated successfully"}

@app.delete("/api/delete-data")
def delete_data():
    try:
        if os.path.exists(CSV_FILE):
            os.remove(CSV_FILE)
        
        # Create a new empty CSV file with headers
        with open(CSV_FILE, mode='w', newline='') as file:
            writer = csv.writer(file)
            writer.writerow(['latitude', 'longitude', 'timestamp', 'signal_strength'])
        
        return {"message": "Data file deleted and reset successfully"}
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"An error occurred: {str(e)}")

@app.post("/api/upload")
async def upload_data(
    latitude: float = Form(...),
    longitude: float = Form(...),
    timestamp: str = Form(...),
    signal_strength: int = Form(...)
):
    try:
        # Validate timestamp format
        datetime.strptime(timestamp, '%Y-%m-%d %H:%M:%S')
        
        with open(CSV_FILE, mode='a', newline='') as file:
            writer = csv.writer(file)
            writer.writerow([latitude, longitude, timestamp, signal_strength])
        
        return {"message": "Data uploaded successfully"}
    except ValueError:
        raise HTTPException(status_code=400, detail="Invalid timestamp format. Use 'YYYY-MM-DD HH:MM:SS'")
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"An error occurred: {str(e)}")

@app.get("/", response_class=HTMLResponse)
def show_map(request: Request, start_date: str = None, end_date: str = None):
    current_user = login_required(request)
    if isinstance(current_user, RedirectResponse):
        return current_user
    
    signal_data = []

    if os.path.exists(CSV_FILE):
        with open(CSV_FILE, mode='r') as file:
            reader = csv.reader(file)
            next(reader)  # Skip header row
            for row in reader:
                lat, lon, timestamp, strength = row
                timestamp = datetime.strptime(timestamp, '%Y-%m-%d %H:%M:%S')
                if start_date and end_date:
                    start_datetime = datetime.strptime(start_date, '%Y-%m-%d')
                    end_datetime = datetime.strptime(end_date, '%Y-%m-%d')
                    if start_datetime <= timestamp <= end_datetime:
                        signal_data.append((float(lat), float(lon), int(strength)))
                else:
                    signal_data.append((float(lat), float(lon), int(strength)))

    if signal_data:
        min_signal = min(s[2] for s in signal_data)
        max_signal = max(s[2] for s in signal_data)
    else:
        min_signal = max_signal = 1

    m = folium.Map(location=[35.6837, 139.6805], zoom_start=12)
    marker_cluster = MarkerCluster().add_to(m)

    for lat, lon, signal_strength in signal_data:
        if max_signal - min_signal != 0:
            radius = 5 + 10 * ((signal_strength - min_signal) / (max_signal - min_signal))
        else:
            radius = 15
        folium.CircleMarker(
            location=[lat, lon],
            radius=radius,
            popup=f'Signal Strength: {signal_strength}',
            color='blue',
            fill=True,
            fill_opacity=0.6
        ).add_to(marker_cluster)
    
    map_html = m._repr_html_()
    return templates.TemplateResponse("map.html", {
        "request": request,
        "map_html": map_html,
        "start_date": start_date,
        "end_date": end_date,
        "current_user": current_user
    })

@app.exception_handler(HTTPException)
async def http_exception_handler(request: Request, exc: HTTPException):
    if exc.status_code == status.HTTP_401_UNAUTHORIZED:
        return RedirectResponse(url="/login", status_code=status.HTTP_302_FOUND)
    return templates.TemplateResponse("error.html", {"request": request, "detail": exc.detail}, status_code=exc.status_code)

@app.get("/download-csv")
async def download_csv():
    if os.path.exists(CSV_FILE):
        return FileResponse(CSV_FILE, filename="wifi_signals.csv")
    raise HTTPException(status_code=404, detail="CSV file not found")

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=7860)

