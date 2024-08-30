# Sử dụng một image chính thức của Python 3.9
FROM python:3.9

# Thiết lập thư mục làm việc cho ứng dụng
WORKDIR /app

# Tạo thư mục cache và thiết lập quyền truy cập
RUN mkdir -p /app/cache/
RUN chmod -R 777 /app

# Đặt biến môi trường cache cho Transformers (nếu cần)
ENV TRANSFORMERS_CACHE=/app/cache/

# Sao chép và cài đặt các dependencies từ requirements.txt
COPY ./requirements.txt /app/requirements.txt
RUN pip install --no-cache-dir --upgrade -r /app/requirements.txt

# Cài đặt thêm các gói cần thiết
RUN apt-get update && apt-get install -y ffmpeg

# Sao chép toàn bộ mã nguồn vào container
COPY . .

# Tạo người dùng không phải root và thiết lập quyền
RUN adduser --disabled-password --gecos '' appuser
RUN adduser appuser sudo
RUN echo '%sudo ALL=(ALL) NOPASSWD:ALL' >> /etc/sudoers

# Đảm bảo quyền truy cập cho người dùng không phải root
RUN chown -R appuser:appuser /app
RUN chmod -R 777 /app

# Chạy ứng dụng với người dùng không phải root
USER appuser

# Expose port 5000 cho FastAPI
EXPOSE 7860

# Chạy ứng dụng FastAPI bằng Uvicorn
CMD ["uvicorn", "app:app", "--host", "0.0.0.0", "--port", "7860"]