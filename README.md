Для работы используется JDK12

Чтобы собрать, запустите команду в корне проекта: gradlew build

Собранный клиент в папке client/dist

Собранный сервер в папке server/build/libs

Чтобы запустить, нужно в папке с jukebox-x.x.x.jar разместить application.properties, содержимое:

```
vk.login={логин вконтакте}
vk.password={пароль вконтакте}
vk.id={id вконтакте}
```

После этого можно запускать jar (для запуска требуется Java >= 8)

Для раздачи клиента лучше использовать nginx, пример конфига:

```
worker_processes  1;
events {
    worker_connections  1024;
}
http {
    include       mime.types;
    default_type  application/octet-stream;
    sendfile        on;
    keepalive_timeout  65;
    server {
        listen       80;
        server_name  localhost;
        location /static/ {
        	alias {путь к клиенту};
        }

        location / {
            root   {путь к клиенту};
            rewrite ^ /index.html break;
        }

        location /api {
        	proxy_pass http://websocket;
        	proxy_http_version 1.1;
    		proxy_set_header Upgrade $http_upgrade;
    		proxy_set_header Connection "upgrade";
    		keepalive_timeout 604800;
			proxy_connect_timeout 604800;
			proxy_send_timeout 604800;
			proxy_read_timeout 604800;
        }

        error_page   500 502 503 504  /50x.html;
        location = /50x.html {
            root   html;
        }
    }

	upstream websocket {
  		server {ip и порт, который слушает сервер};
	}
}
```
