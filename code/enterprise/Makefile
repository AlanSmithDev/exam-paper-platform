.PHONY: build test up down dev-web dev-admin

build:        ## 编译全部微服务
	mvn -q package -DskipTests

test:         ## 全量单测（含组卷引擎）
	mvn -q test

up:           ## 启动全栈（数据层+5服务）
	cd docker && docker compose up -d

down:
	cd docker && docker compose down

dev-web:      ## 教师端开发模式
	cd frontend && npm i && npm run dev

dev-admin:    ## 超管端开发模式
	cd frontend-admin && npm i && npm run dev
