#!/bin/bash
# 智卷云端到端冒烟（Docker 部署验证，docs/22 §2.18）：经网关 http://localhost:8080 逐域验证
set -u
BASE="http://localhost:8081/api/v1"
PASS=0; FAIL=0; FAILED_CASES=""

# JSON 字段提取（无 jq 环境用 python）
jget() { python -c "import sys,json;d=json.load(sys.stdin);print(eval('d'+sys.argv[1]))" "$1" 2>/dev/null; }
jgets() { python -c "import sys,json;d=json.load(sys.stdin);print(eval('d'+sys.argv[1]))" "$1" 2>/dev/null; }

check() { # check <名称> <条件表达式结果 0/1>
  if [ "$2" = "0" ]; then PASS=$((PASS+1)); echo "  ✅ $1";
  else FAIL=$((FAIL+1)); FAILED_CASES="$FAILED_CASES\n    - $1"; echo "  ❌ $1"; fi
}

echo "==== 1. 认证与防爆破 ===="
REG=$(curl -s -X POST $BASE/auth/register -H 'Content-Type: application/json' \
  -d '{"mobile":"13900001001","password":"Smoke@2026","nickname":"冒烟教师","role":"TEACHER"}')
TT=$(echo "$REG" | jget "['data']['accessToken']")
check "教师注册→token" $([ -n "$TT" ] && [ "$TT" != "None" ] && echo 0 || echo 1)

REG2=$(curl -s -X POST $BASE/auth/register -H 'Content-Type: application/json' \
  -d '{"mobile":"13900001002","password":"Smoke@2026","nickname":"冒烟学生","role":"STUDENT"}')
ST=$(echo "$REG2" | jget "['data']['accessToken']")
check "学生注册→token" $([ -n "$ST" ] && [ "$ST" != "None" ] && echo 0 || echo 1)

LG=$(curl -s -X POST $BASE/auth/login -H 'Content-Type: application/json' -H 'X-Forwarded-For: 10.9.9.9' \
  -d '{"mobile":"13900001001","password":"WRONG"}')
echo "$LG" | grep -q '手机号或密码错误'; check "错误密码被拒" $?
# 防爆破：连续 5 次失败后锁定
for i in 1 2 3 4; do curl -s -X POST $BASE/auth/login -H 'Content-Type: application/json' -H 'X-Forwarded-For: 10.9.9.9' \
  -d '{"mobile":"13900001001","password":"WRONG"}' >/dev/null; done
LOCK=$(curl -s -X POST $BASE/auth/login -H 'Content-Type: application/json' -H 'X-Forwarded-For: 10.9.9.9' \
  -d '{"mobile":"13900001001","password":"WRONG"}')
echo "$LOCK" | grep -q '临时锁定'; check "防爆破：5 次失败后临时锁定" $?
# 换源（不同 X-Forwarded-For）不受影响
TT=$(curl -s -X POST $BASE/auth/login -H 'Content-Type: application/json' -H 'X-Forwarded-For: 10.8.8.8' \
  -d '{"mobile":"13900001001","password":"Smoke@2026"}' | jget "['data']['accessToken']")
check "教师正常登录（换源不受锁定影响）" $([ -n "$TT" ] && [ "$TT" != "None" ] && echo 0 || echo 1)

echo "==== 2. 题库与组卷 ===="
QS=$(curl -s "$BASE/questions?pageSize=5")
QID=$(echo "$QS" | jget "['data']['rows'][0]['id']")
[ -z "$QID" ] || [ "$QID" = "None" ] && QID=$(echo "$QS" | jget "['data']['list'][0]['id']")
check "题库列表（种子题）" $([ -n "$QID" ] && [ "$QID" != "None" ] && echo 0 || echo 1)

GEN=$(curl -s -X POST $BASE/papers/generate -H "Authorization: Bearer $TT" -H 'Content-Type: application/json' \
  -d '{"title":"冒烟·智能组卷","structure":[{"type":"单选题","count":3,"score":5},{"type":"解答题","count":1,"score":10}]}')
PID=$(echo "$GEN" | jget "['data']['paperId']")
check "智能组卷→paperId" $([ -n "$PID" ] && [ "$PID" != "None" ] && echo 0 || echo 1)

echo "==== 3. 试卷模板（TJ-80）===="
STPL=$(curl -s -X POST $BASE/papers/$PID/save-template -H "Authorization: Bearer $TT" -H 'Content-Type: application/json' -d '{"name":"冒烟模板A"}')
TID=$(echo "$STPL" | jget "['data']['templateId']")
check "存为模板" $([ -n "$TID" ] && [ "$TID" != "None" ] && echo 0 || echo 1)
APPLY=$(curl -s -X POST $BASE/papers/templates/$TID/apply -H "Authorization: Bearer $TT" -H 'Content-Type: application/json' -d '{}')
NEWPID=$(echo "$APPLY" | jget "['data']['paperId']")
check "模板出卷→新卷" $([ -n "$NEWPID" ] && [ "$NEWPID" != "None" ] && echo 0 || echo 1)
curl -s -X DELETE $BASE/papers/templates/$TID -H "Authorization: Bearer $TT" >/dev/null
check "删除模板" $?

echo "==== 4. 导出（C6 版面参数）===="
EXP=$(curl -s -X POST $BASE/papers/$NEWPID/export -H "Authorization: Bearer $TT" -H 'Content-Type: application/json' \
  -d '{"paper":"A4","columns":1,"answerMode":"SEPARATED"}')
URL=$(echo "$EXP" | jget "['data']['downloadUrl']")
check "导出（答案分离）→下载地址" $([ -n "$URL" ] && [ "$URL" != "None" ] && echo 0 || echo 1)
FILE=$(basename "$URL")
curl -s -o /dev/null -w '%{http_code}' "$BASE/papers/export/download/$FILE" | grep -q 200; check "下载产物 200" $?

echo "==== 5. 作业闭环（e卷通）===="
AS=$(curl -s -X POST $BASE/assignments -H "Authorization: Bearer $TT" -H 'Content-Type: application/json' \
  -d "{\"title\":\"冒烟作业\",\"questionIds\":[$QID],\"deadlineHours\":24}")
AID=$(echo "$AS" | jget "['data']['assignmentId']")
check "布置作业" $([ -n "$AID" ] && [ "$AID" != "None" ] && echo 0 || echo 1)
curl -s -X POST $BASE/assignments/$AID/publish -H "Authorization: Bearer $TT" >/dev/null
curl -s -X POST $BASE/assignments/$AID/students -H "Authorization: Bearer $TT" -H 'Content-Type: application/json' \
  -d '{"studentIds":[2]}' >/dev/null
SUB=$(curl -s -X POST $BASE/assignments/$AID/submit -H "Authorization: Bearer $ST" -H 'Content-Type: application/json' \
  -d "[{\"questionId\":$QID,\"answer\":\"A\"}]")
echo "$SUB" | grep -q 'total'; check "学生作答提交" $?
GR=$(curl -s "$BASE/assignments/$AID/report" -H "Authorization: Bearer $TT")
echo "$GR" | grep -q 'roster'; check "班级报告" $?
REP=$(curl -s "$BASE/assignments/my/report" -H "Authorization: Bearer $ST")
echo "$REP" | grep -q 'overall'; check "学生个体学情报告" $?

echo "==== 6. AI 三件套 ===="
SR=$(curl -s -X POST $BASE/ai/search -H "Authorization: Bearer $TT" -H 'Content-Type: application/json' \
  -d '{"query":"高二 立体几何 解答题 较难"}')
echo "$SR" | grep -q 'parsed'; check "AI 搜（意图解析+检索）" $?
PH=$(curl -s -X POST $BASE/ai/photo-search -H "Authorization: Bearer $TT" -H 'Content-Type: application/json' \
  -d '{"imageBase64":"aGVsbG8=","mime":"image/png"}')
echo "$PH" | grep -q 'recognized'; check "拍照搜题（MOCK 全链）" $?
TD=$(curl -s -X POST $BASE/ai/teaching-design -H "Authorization: Bearer $TT" -H 'Content-Type: application/json' \
  -d '{"kp":"函数的单调性","gradeLevel":"高中","subject":"数学"}')
echo "$TD" | grep -q 'objectives'; check "AI 教学设计草稿（TJ-42）" $?

echo "==== 7. 支付沙箱（D14）===="
ORD=$(curl -s -X POST $BASE/member/orders -H "Authorization: Bearer $ST" -H 'Content-Type: application/json' \
  -H "X-Idempotency-Key: smoke-$(date +%s)" -d '{"skuType":"MEMBER","skuRef":1,"quantity":1}')
ONO=$(echo "$ORD" | jget "['data']['orderNo']")
check "会员下单" $([ -n "$ONO" ] && [ "$ONO" != "None" ] && echo 0 || echo 1)
PP=$(curl -s -X POST $BASE/payments/$ONO/prepay -H "Authorization: Bearer $ST" -H 'Content-Type: application/json' -d '{}')
echo "$PP" | grep -q 'MOCK_NOTIFY'; check "prepay→沙箱凭证" $?
NT=$(curl -s -X POST $BASE/payments/mock-notify -H 'Content-Type: application/json' -d "{\"orderNo\":\"$ONO\"}")
echo "$NT" | grep -q 'PAID'; check "沙箱回调→PAID" $?
echo "$NT" | grep -q '"idempotent":false'; check "回调计数器（首次非幂等）" $?

echo "==== 8. 管理端与 TOTP ===="
AL=$(curl -s -X POST $BASE/auth/admin-login -H 'Content-Type: application/json' -H 'X-Forwarded-For: 10.7.7.7' \
  -d '{"mobile":"13000000000","password":"Admin@123456"}')
AT=$(echo "$AL" | jget "['data']['accessToken']")
check "超管登录（种子账号）" $([ -n "$AT" ] && [ "$AT" != "None" ] && echo 0 || echo 1)
SU=$(curl -s -X POST $BASE/auth/totp/setup -H "Authorization: Bearer $AT" -H 'Content-Type: application/json' -d '{}')
SECRET=$(echo "$SU" | jget "['data']['secret']")
check "TOTP setup" $([ -n "$SECRET" ] && [ "$SECRET" != "None" ] && echo 0 || echo 1)
CODE=$(python -c "
import hmac,hashlib,struct,time,base64
key=base64.b32decode('$SECRET'); step=int(time.time())//30
hmacval=hmac.new(key,struct.pack('>Q',step),hashlib.sha256).digest()
o=hmacval[-1]&15; print('%06d'%(((struct.unpack('>I',hmacval[o:o+4])[0]&0x7fffffff)%1000000)))")
EN=$(curl -s -X POST $BASE/auth/totp/enable -H "Authorization: Bearer $AT" -H 'Content-Type: application/json' -d "{\"code\":\"$CODE\"}")
echo "$EN" | grep -q '"enabled":true'; check "TOTP enable（动态码验证）" $?
NT=$(curl -s -X POST $BASE/auth/admin-login -H 'Content-Type: application/json' -H 'X-Forwarded-For: 10.7.7.7' \
  -d '{"mobile":"13000000000","password":"Admin@123456"}')
echo "$NT" | grep -q 'NEED_TOTP'; check "未带动态码→NEED_TOTP 拒绝" $?
AT2=$(curl -s -X POST $BASE/auth/admin-login -H 'Content-Type: application/json' -H 'X-Forwarded-For: 10.7.7.7' \
  -d "{\"mobile\":\"13000000000\",\"password\":\"Admin@123456\",\"totp\":\"$CODE\"}" | jget "['data']['accessToken']")
check "带动态码→登录成功" $([ -n "$AT2" ] && [ "$AT2" != "None" ] && echo 0 || echo 1)
AT="$AT2"   # 后续用带 TOTP 流程取到的新 token

echo "==== 9. 学校订阅（T-26h）===="
SC=$(curl -s -X POST $BASE/schools/admin -H "Authorization: Bearer $AT" -H 'Content-Type: application/json' \
  -d '{"name":"冒烟一中","adminUserId":1,"months":12}')
SCID=$(echo "$SC" | jget "['data']['schoolId']")
check "学校开通" $([ -n "$SCID" ] && [ "$SCID" != "None" ] && echo 0 || echo 1)
ENT=$(curl -s "$BASE/trade/entitlement" -H "Authorization: Bearer $TT")
echo "$ENT" | grep -q 'schoolActive'; check "教师权益联动字段返回" $?

echo "==== 10. G3 可观测性 ===="
UP=$(curl -s http://localhost:9090/api/v1/query?query=up 2>/dev/null | jget "['data']['result']" 2>/dev/null)
PRC=$(curl -s -o /dev/null -w '%{http_code}' http://localhost:9090/-/ready)
check "Prometheus ready" $([ "$PRC" = "200" ] && echo 0 || echo 1)
GRC=$(curl -s -o /dev/null -w '%{http_code}' http://localhost:3001/api/health)
check "Grafana ready" $([ "$GRC" = "200" ] && echo 0 || echo 1)
ACT=$(curl -s -o /dev/null -w '%{http_code}' http://localhost:8105/actuator/prometheus)
check "服务指标暴露（trade:8105）" $([ "$ACT" = "200" ] && echo 0 || echo 1)

echo ""
echo "================ 冒烟结果：PASS=$PASS FAIL=$FAIL ================"
[ -n "$FAILED_CASES" ] && echo -e "失败项：$FAILED_CASES"
exit $([ $FAIL -eq 0 ] && echo 0 || echo 1)
