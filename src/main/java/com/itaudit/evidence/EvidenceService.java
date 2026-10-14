package com.itaudit.evidence;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.itaudit.auth.LoginUser;
import com.itaudit.common.ActivityLogService;
import com.itaudit.common.BusinessException;

/**
 * 증적 파일 저장·조회·논리 삭제.
 * 파일은 {data-dir}/files/yyyy/MM/무작위이름.확장자 로 저장하고, 원래 파일명은 DB 에만 둔다.
 */
@Service
public class EvidenceService {

    /** PRD 8장 허용 확장자. jpeg 는 jpg 의 다른 표기라 함께 허용한다. */
    public static final Set<String> ALLOWED_EXTENSIONS = Set.of("pdf", "xlsx", "docx", "png", "jpg", "jpeg", "zip", "txt");
    public static final long MAX_FILE_SIZE = 50L * 1024 * 1024;

    private final EvidenceMapper mapper;
    private final ActivityLogService activityLog;
    private final Path dataDir;
    private final Map<EvidenceOwnerType, EvidenceAccess> accessRules = new EnumMap<>(EvidenceOwnerType.class);

    public EvidenceService(EvidenceMapper mapper, ActivityLogService activityLog,
                           @Value("${app.data-dir}") String dataDir, List<EvidenceAccess> accessRules) {
        this.mapper = mapper;
        this.activityLog = activityLog;
        this.dataDir = Path.of(dataDir).toAbsolutePath().normalize();
        accessRules.forEach(rule -> this.accessRules.put(rule.ownerType(), rule));
    }

    public List<Evidence> findActive(EvidenceOwnerType ownerType, Long ownerId) {
        return mapper.findActive(ownerType, ownerId);
    }

    /** 파일 검증만 (저장 전 한꺼번에 확인용) */
    public void validate(MultipartFile file) {
        String name = originalName(file);
        if (file.isEmpty()) {
            throw new BusinessException("빈 파일은 올릴 수 없습니다: " + name);
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BusinessException("파일은 1건당 50MB 이하여야 합니다: " + name);
        }
        String ext = extension(name);
        if (!ALLOWED_EXTENSIONS.contains(ext)) {
            throw new BusinessException("올릴 수 없는 파일 형식입니다: " + name
                    + " (허용: pdf, xlsx, docx, png, jpg, zip, txt)");
        }
        if (name.length() > 255) {
            throw new BusinessException("파일명은 255자 이하여야 합니다.");
        }
    }

    /** 접근 확인 없이 저장 (호출하는 기능이 권한을 먼저 확인한다) */
    @Transactional
    public Evidence store(EvidenceOwnerType ownerType, Long ownerId, MultipartFile file, LoginUser actor) {
        validate(file);
        String name = originalName(file);
        LocalDate today = LocalDate.now();
        String relative = String.format("files/%d/%02d/%s.%s", today.getYear(), today.getMonthValue(),
                UUID.randomUUID(), extension(name));
        Path target = resolve(relative);
        try (InputStream in = file.getInputStream()) {
            Files.createDirectories(target.getParent());
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new BusinessException("파일을 저장하지 못했습니다: " + name);
        }

        Evidence ev = new Evidence();
        ev.setOwnerType(ownerType);
        ev.setOwnerId(ownerId);
        ev.setOriginalName(name);
        ev.setStoredPath(relative);
        ev.setFileSize(file.getSize());
        ev.setContentType(file.getContentType());
        ev.setUploadedBy(actor.getUserId());
        ev.setUploadedAt(LocalDateTime.now());
        mapper.insert(ev);
        activityLog.log(ActivityLogService.EVIDENCE_UPLOAD, ownerType.name(), ownerId,
                "파일=" + name + " (" + ev.getSizeLabel() + "), 증적ID=" + ev.getEvidenceId());
        return ev;
    }

    /** 내려받기: 소유 대상 열람 권한 확인 후 파일 */
    public Evidence getForDownload(Long evidenceId, LoginUser user) {
        Evidence ev = mapper.findById(evidenceId);
        if (ev == null || ev.isDeleted()) {
            throw new BusinessException("파일을 찾을 수 없습니다.");
        }
        access(ev.getOwnerType()).checkView(ev.getOwnerId(), user);
        return ev;
    }

    public Resource open(Evidence ev) {
        Path path = resolve(ev.getStoredPath());
        if (!Files.isRegularFile(path)) {
            throw new BusinessException("파일이 저장소에 없습니다: " + ev.getOriginalName());
        }
        return new FileSystemResource(path);
    }

    /** 논리 삭제: 파일과 메타데이터는 남기고 삭제 표시 + 이력 */
    @Transactional
    public Evidence delete(Long evidenceId, LoginUser actor) {
        Evidence ev = mapper.findById(evidenceId);
        if (ev == null || ev.isDeleted()) {
            throw new BusinessException("파일을 찾을 수 없습니다.");
        }
        access(ev.getOwnerType()).checkModify(ev.getOwnerId(), actor);
        mapper.markDeleted(evidenceId, actor.getUserId(), LocalDateTime.now());
        activityLog.log(ActivityLogService.EVIDENCE_DELETE, ev.getOwnerType().name(), ev.getOwnerId(),
                "파일=" + ev.getOriginalName() + ", 증적ID=" + evidenceId);
        return ev;
    }

    /**
     * 저장된 파일 물리 삭제 (회차 삭제처럼 대상 자체가 사라질 때만 쓴다). 실패한 파일은 로그만 남기고 계속한다.
     * 경로가 data 디렉터리 밖이면 건너뛴다.
     */
    public int deleteStoredFiles(List<String> storedPaths) {
        int deleted = 0;
        for (String relative : storedPaths) {
            try {
                if (Files.deleteIfExists(resolve(relative))) {
                    deleted++;
                }
            } catch (IOException | BusinessException e) {
                org.slf4j.LoggerFactory.getLogger(EvidenceService.class).warn("증적 파일 삭제 실패: {}", relative);
            }
        }
        return deleted;
    }

    private EvidenceAccess access(EvidenceOwnerType type) {
        EvidenceAccess rule = accessRules.get(type);
        if (rule == null) {
            throw new BusinessException("지원하지 않는 첨부 대상입니다.");
        }
        return rule;
    }

    /** data 디렉터리 밖을 가리키는 경로는 거부 */
    private Path resolve(String relative) {
        Path path = dataDir.resolve(relative).normalize();
        if (!path.startsWith(dataDir)) {
            throw new BusinessException("잘못된 파일 경로입니다.");
        }
        return path;
    }

    /** 브라우저가 보낸 이름에서 경로 부분을 떼어 낸 파일명 */
    static String originalName(MultipartFile file) {
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        name = name.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1).trim();
        return name.isEmpty() ? "file" : name;
    }

    static String extension(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
