package com.examforge.school.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.examforge.api.feign.UserStatsClient;
import com.examforge.common.web.GlobalExceptionHandler.BizException;
import com.examforge.common.web.Result;
import com.examforge.school.domain.School;
import com.examforge.school.domain.SchoolMember;
import com.examforge.school.logic.SchoolRules;
import com.examforge.school.mapper.SchoolMapper;
import com.examforge.school.mapper.SchoolMemberMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/** 学校订阅核心服务（T-26h：超管开通 → 校管理员管理成员 → 教师全员享会员权益，docs/26 §7） */
@Slf4j
@Service
@RequiredArgsConstructor
public class SchoolService {

    private final SchoolMapper schoolMapper;
    private final SchoolMemberMapper memberMapper;
    private final UserStatsClient userStatsClient;

    // ---------- 超管侧 ----------

    /** 开通学校（B 端合同开通口径）：{name, adminUserId, seatLimit?, months?} */
    public Map<String, Object> open(Map<String, Object> body) {
        String name = String.valueOf(body.getOrDefault("name", "")).trim();
        if (name.isBlank()) throw new BizException(Result.BAD_REQUEST, "学校名称必填");
        Long adminUserId = toLong(body.get("adminUserId"));
        if (adminUserId == null || adminUserId <= 0) throw new BizException(Result.BAD_REQUEST, "校管理员用户ID必填");
        Integer seatLimit = toLong(body.get("seatLimit")) == null ? null : toLong(body.get("seatLimit")).intValue();

        School s = new School();
        s.setName(name);
        s.setAdminUserId(adminUserId);
        s.setStatus(School.OPEN);
        s.setSeatLimit(seatLimit == null || seatLimit <= 0 ? null : seatLimit);
        int months = body.get("months") == null ? 12 : Integer.parseInt(String.valueOf(body.get("months")));
        s.setMemberUntil(SchoolRules.renewUntil(null, months, LocalDateTime.now()));
        s.setCreatedAt(LocalDateTime.now());
        try {
            schoolMapper.insert(s);
        } catch (DuplicateKeyException e) {
            throw new BizException(Result.BAD_REQUEST, "该用户已是其他学校的管理员");
        }
        // 管理员自身即成员（教师角色，享权益）
        insertMember(s.getId(), adminUserId, SchoolMember.TEACHER);
        log.info("学校开通: id={} name={} admin={} 席位={} 期限至{}", s.getId(), name, adminUserId, s.getSeatLimit(), s.getMemberUntil());
        return Map.of("schoolId", s.getId(), "memberUntil", s.getMemberUntil().toString());
    }

    public List<Map<String, Object>> list() {
        List<School> schools = schoolMapper.selectList(new LambdaQueryWrapper<School>()
                .orderByDesc(School::getId));
        Map<Long, Long> teacherCount = teacherCounts(schools.stream().map(School::getId).toList());
        return schools.stream().map(s -> Map.<String, Object>of(
                "id", s.getId(), "name", s.getName(), "adminUserId", s.getAdminUserId(),
                "status", s.getStatus() == School.OPEN ? "OPEN" : "CLOSED",
                "active", SchoolRules.isActive(s.getStatus(), s.getMemberUntil(), LocalDateTime.now()),
                "seatLimit", s.getSeatLimit() == null ? -1 : s.getSeatLimit(),
                "teachers", teacherCount.getOrDefault(s.getId(), 0L),
                "memberUntil", s.getMemberUntil() == null ? "" : s.getMemberUntil().toString())).toList();
    }

    /** 续订 N 个月（未到期顺延，已到期从现在起算） */
    public Map<String, Object> renew(Long id, Integer months) {
        School s = schoolMapper.selectById(id);
        if (s == null) throw new BizException(Result.NOT_FOUND, "学校不存在");
        s.setMemberUntil(SchoolRules.renewUntil(s.getMemberUntil(), months == null ? 12 : months, LocalDateTime.now()));
        s.setStatus(School.OPEN);
        schoolMapper.updateById(s);
        return Map.of("schoolId", s.getId(), "memberUntil", s.getMemberUntil().toString());
    }

    public Map<String, Object> close(Long id) {
        School s = schoolMapper.selectById(id);
        if (s == null) throw new BizException(Result.NOT_FOUND, "学校不存在");
        s.setStatus(School.CLOSED);
        schoolMapper.updateById(s);
        return Map.of("schoolId", s.getId(), "status", "CLOSED");
    }

    // ---------- 校管理员侧 ----------

    /** 我的学校概览（仅校管理员可见） */
    public Map<String, Object> mine(Long viewerId) {
        School s = schoolMapper.selectOne(new LambdaQueryWrapper<School>()
                .eq(School::getAdminUserId, viewerId));
        if (s == null) throw new BizException(Result.NOT_FOUND, "你还不是任何学校的管理员");
        long teachers = memberMapper.selectCount(new LambdaQueryWrapper<SchoolMember>()
                .eq(SchoolMember::getSchoolId, s.getId()).eq(SchoolMember::getRole, SchoolMember.TEACHER));
        return Map.of("id", s.getId(), "name", s.getName(),
                "status", s.getStatus() == School.OPEN ? "OPEN" : "CLOSED",
                "active", SchoolRules.isActive(s.getStatus(), s.getMemberUntil(), LocalDateTime.now()),
                "seatLimit", s.getSeatLimit() == null ? -1 : s.getSeatLimit(),
                "teachers", teachers, "seatLeft", s.getSeatLimit() == null ? -1 : Math.max(0, s.getSeatLimit() - teachers),
                "memberUntil", s.getMemberUntil() == null ? "" : s.getMemberUntil().toString());
    }

    /** 批量添加成员：{members:[{userId,role?}]}；席位上限/用户存在性/去重校验 */
    @Transactional
    public Map<String, Object> addMembers(Long viewerId, List<Map<String, Object>> members) {
        School s = ownedBy(viewerId);
        if (members == null || members.isEmpty()) throw new BizException(Result.BAD_REQUEST, "成员列表不能为空");
        List<Long> userIds = SchoolRules.normalizeBatch(
                members.stream().map(m -> toLong(m.get("userId"))).toList());
        if (userIds.isEmpty()) throw new BizException(Result.BAD_REQUEST, "无有效成员ID");

        // 用户存在性校验（fail-closed：用户服务不可用则拒绝写入，保证成员记录可追溯）
        Set<Long> exists = new HashSet<>();
        try {
            exists.addAll(userStatsClient.batch(userIds.stream().map(String::valueOf)
                            .collect(Collectors.joining(","))).stream()
                    .map(m -> toLong(m.get("id"))).filter(Objects::nonNull)
                    .collect(Collectors.toSet()));
        } catch (Exception e) {
            throw new BizException(Result.SYSTEM, "用户服务不可用，请稍后重试");
        }

        long currentTeachers = memberMapper.selectCount(new LambdaQueryWrapper<SchoolMember>()
                .eq(SchoolMember::getSchoolId, s.getId()).eq(SchoolMember::getRole, SchoolMember.TEACHER));
        int added = 0, skipped = 0;
        for (Map<String, Object> m : members) {
            Long userId = toLong(m.get("userId"));
            if (userId == null || !exists.contains(userId)) { skipped++; continue; }
            String role = SchoolRules.normalizeRole(m.get("role") == null ? null : String.valueOf(m.get("role")));
            if (userId.equals(s.getAdminUserId()) || userId.equals(viewerId)) role = SchoolMember.TEACHER;
            if (SchoolMember.TEACHER.equals(role) && !SchoolRules.canAddTeacher(currentTeachers, s.getSeatLimit())) {
                skipped++;   // 席位已满，静默跳过并在结果中说明
                continue;
            }
            if (insertMember(s.getId(), userId, role)) {
                added++;
                if (SchoolMember.TEACHER.equals(role)) currentTeachers++;
            } else {
                skipped++;   // 重复成员
            }
        }
        return Map.of("added", added, "skipped", skipped, "teachers", currentTeachers,
                "seatLeft", s.getSeatLimit() == null ? -1 : Math.max(0, s.getSeatLimit() - currentTeachers));
    }

    public List<Map<String, Object>> listMembers(Long viewerId) {
        School s = ownedBy(viewerId);
        return memberMapper.selectList(new LambdaQueryWrapper<SchoolMember>()
                        .eq(SchoolMember::getSchoolId, s.getId()).orderByAsc(SchoolMember::getId)).stream()
                .map(m -> Map.<String, Object>of("userId", m.getUserId(), "role", m.getRole(),
                        "joinedAt", m.getJoinedAt() == null ? "" : m.getJoinedAt().toString()))
                .toList();
    }

    public Map<String, Object> removeMember(Long viewerId, Long userId) {
        School s = ownedBy(viewerId);
        if (userId.equals(s.getAdminUserId())) throw new BizException(Result.BAD_REQUEST, "不能移除校管理员本人");
        int n = memberMapper.delete(new LambdaQueryWrapper<SchoolMember>()
                .eq(SchoolMember::getSchoolId, s.getId()).eq(SchoolMember::getUserId, userId));
        return Map.of("removed", n);
    }

    // ---------- 内部（trade 权益联动） ----------

    /** 用户学校身份：取最新一条有效订阅成员记录（含学校名/角色），无有效订阅返回 active=false */
    public Map<String, Object> membership(Long userId) {
        List<SchoolMember> rows = memberMapper.selectList(new LambdaQueryWrapper<SchoolMember>()
                .eq(SchoolMember::getUserId, userId).orderByDesc(SchoolMember::getId).last("LIMIT 5"));
        for (SchoolMember m : rows) {
            School s = schoolMapper.selectById(m.getSchoolId());
            if (s != null && SchoolRules.isActive(s.getStatus(), s.getMemberUntil(), LocalDateTime.now())) {
                return Map.of("active", true, "schoolId", s.getId(), "schoolName", s.getName(), "role", m.getRole());
            }
        }
        return Map.of("active", false);
    }

    // ---------- 内部方法 ----------

    private School ownedBy(Long viewerId) {
        School s = schoolMapper.selectOne(new LambdaQueryWrapper<School>()
                .eq(School::getAdminUserId, viewerId));
        if (s == null) throw new BizException(Result.FORBIDDEN, "你还不是任何学校的管理员");
        return s;
    }

    private boolean insertMember(Long schoolId, Long userId, String role) {
        SchoolMember m = new SchoolMember();
        m.setSchoolId(schoolId);
        m.setUserId(userId);
        m.setRole(role);
        m.setJoinedAt(LocalDateTime.now());
        try {
            memberMapper.insert(m);
            return true;
        } catch (DuplicateKeyException e) {
            return false;   // 重复成员幂等跳过
        }
    }

    private Map<Long, Long> teacherCounts(List<Long> schoolIds) {
        if (schoolIds.isEmpty()) return Map.of();
        return memberMapper.selectList(new LambdaQueryWrapper<SchoolMember>()
                        .in(SchoolMember::getSchoolId, schoolIds).eq(SchoolMember::getRole, SchoolMember.TEACHER))
                .stream().collect(Collectors.groupingBy(SchoolMember::getSchoolId, Collectors.counting()));
    }

    private Long toLong(Object o) {
        if (o == null) return null;
        try { return Long.valueOf(String.valueOf(o)); } catch (NumberFormatException e) { return null; }
    }
}
