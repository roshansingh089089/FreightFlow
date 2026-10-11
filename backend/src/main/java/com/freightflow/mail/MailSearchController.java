package com.freightflow.mail;

import com.freightflow.common.exception.ApiException;
import com.freightflow.common.security.SecurityConfig;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/mail/search")
class MailSearchController {
  private final MailService mail;
  private final NamedParameterJdbcTemplate jdbc;

  MailSearchController(MailService mail, NamedParameterJdbcTemplate jdbc) {
    this.mail = mail;
    this.jdbc = jdbc;
  }

  record SearchItem(
      UUID id,
      UUID threadId,
      String direction,
      String fromAddress,
      String recipients,
      String subject,
      String status,
      boolean read,
      UUID customerId,
      UUID quotationId,
      Instant at) {}

  @PreAuthorize("@permissions.can('mail.read')")
  @GetMapping
  Page<SearchItem> search(
      @RequestParam(defaultValue = "") String sender,
      @RequestParam(defaultValue = "") String recipient,
      @RequestParam(defaultValue = "") String subject,
      @RequestParam(defaultValue = "") String direction,
      @RequestParam(defaultValue = "") String status,
      @RequestParam(required = false) UUID customerId,
      @RequestParam(required = false) UUID quotationId,
      @RequestParam(required = false) Instant from,
      @RequestParam(required = false) Instant to,
      @RequestParam(required = false) Boolean read,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    var user = SecurityConfig.current();
    var connection = mail.active(user);
    if (from != null && to != null && !from.isBefore(to))
      throw new ApiException(400, "MAIL_DATE_RANGE_INVALID", "End date must follow start date");
    int safePage = Math.max(0, page), safeSize = Math.min(100, Math.max(1, size));
    String base =
        """
        with matched as (
          select e.id,e.thread_id,'INBOUND' as direction,e.from_email as from_address,
                 concat_ws(' ',e.to_email,e.cc_email) as recipients,e.subject,
                 'RECEIVED' as status,e.is_read as is_read,t.customer_id,
                 null::uuid as quotation_id,e.received_at as activity_at
          from email_messages e left join mail_threads t on t.id=e.thread_id and t.tenant_id=e.tenant_id
          where e.tenant_id=:tenant and e.connection_id=:connection
          union all
          select o.id,o.thread_id,'OUTBOUND' as direction,o.from_address,
                 coalesce((select string_agg(r.email,' ') from outbound_recipients r
                           where r.tenant_id=o.tenant_id and r.message_id=o.id),'') as recipients,
                 o.subject,o.status,true as is_read,coalesce(o.customer_id,t.customer_id) as customer_id,
                 o.quotation_id,coalesce(o.sent_at,o.created_at) as activity_at
          from outbound_messages o left join mail_threads t on t.id=o.thread_id and t.tenant_id=o.tenant_id
          where o.tenant_id=:tenant and o.created_by=:user
            and (o.connection_id=:connection or (o.connection_id is null and o.status='DRAFT'))
        ) select * from matched where 1=1
        """;
    StringBuilder sql = new StringBuilder(base);
    Map<String, Object> args = new HashMap<>();
    args.put("tenant", user.getTenantId());
    args.put("user", user.getId());
    args.put("connection", connection.getId());
    addLike(sql, args, "sender", "from_address", sender);
    addLike(sql, args, "recipient", "recipients", recipient);
    addLike(sql, args, "subject", "subject", subject);
    if (!direction.isBlank()) {
      sql.append(" and direction=:direction");
      args.put("direction", direction);
    }
    if (!status.isBlank()) {
      sql.append(" and status=:status");
      args.put("status", status);
    }
    if (customerId != null) {
      sql.append(" and customer_id=:customer");
      args.put("customer", customerId);
    }
    if (quotationId != null) {
      sql.append(" and quotation_id=:quotation");
      args.put("quotation", quotationId);
    }
    if (from != null) {
      sql.append(" and activity_at>=:from");
      args.put("from", from.atOffset(ZoneOffset.UTC));
    }
    if (to != null) {
      sql.append(" and activity_at<:to");
      args.put("to", to.atOffset(ZoneOffset.UTC));
    }
    if (read != null) {
      sql.append(" and is_read=:read");
      args.put("read", read);
    }
    Long count =
        jdbc.queryForObject("select count(*) from (" + sql + ") results", args, Long.class);
    sql.append(" order by activity_at desc,id desc limit :limit offset :offset");
    args.put("limit", safeSize);
    args.put("offset", (long) safePage * safeSize);
    List<SearchItem> rows = jdbc.query(sql.toString(), args, MailSearchController::map);
    return new PageImpl<>(rows, PageRequest.of(safePage, safeSize), count == null ? 0 : count);
  }

  private static void addLike(
      StringBuilder sql, Map<String, Object> args, String param, String column, String value) {
    if (value == null || value.isBlank()) return;
    sql.append(" and lower(coalesce(").append(column).append(",'')) like :").append(param);
    args.put(param, "%" + value.trim().toLowerCase().replace("%", "\\%").replace("_", "\\_") + "%");
  }

  private static SearchItem map(ResultSet row, int ignored) throws SQLException {
    return new SearchItem(
        row.getObject("id", UUID.class),
        row.getObject("thread_id", UUID.class),
        row.getString("direction"),
        row.getString("from_address"),
        row.getString("recipients"),
        row.getString("subject"),
        row.getString("status"),
        row.getBoolean("is_read"),
        row.getObject("customer_id", UUID.class),
        row.getObject("quotation_id", UUID.class),
        row.getTimestamp("activity_at").toInstant());
  }
}
