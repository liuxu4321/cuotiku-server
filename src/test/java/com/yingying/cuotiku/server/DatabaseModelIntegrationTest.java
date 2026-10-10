package com.yingying.cuotiku.server;

import com.yingying.cuotiku.server.entity.*;
import jakarta.persistence.EntityManager;
import org.hibernate.Hibernate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Real MySQL verifies FK navigation, JSON types, generated scope and historical compatibility. */
@SpringBootTest
@TestPropertySource(properties = {
        "BOOK_LOCAL_DIR=target/test-data-model/storage",
        "app.jwt.secret=test-secret-key-for-integration-tests-0123456789abcdef",
        "app.admin.phone=13800000000", "app.admin.password=admin123456",
        "app.cos.bucket=", "app.ai.tencent.secret-id=", "app.ai.tencent.secret-key=",
        "app.cos.secret-id=", "app.cos.secret-key=",
        "spring.jpa.properties.hibernate.hbm2ddl.halt_on_error=true"
})
@Transactional
class DatabaseModelIntegrationTest extends AbstractIntegrationTest {
    @Autowired EntityManager em;
    @Autowired com.fasterxml.jackson.databind.ObjectMapper mapper;

    private User user() {
        User user = new User();
        user.setPhone("139" + String.valueOf(System.nanoTime()).substring(0, 8));
        user.setPassword("test-hash"); user.setMemberName("会员星星");
        em.persist(user); em.flush();
        return user;
    }
    private StudentProfile student(User user, String name) {
        StudentProfile student = new StudentProfile();
        student.setUserId(user.getId()); student.setNickname(name); student.setGrade(5);
        em.persist(student); em.flush(); return student;
    }
    private MediaAsset asset(User user, StudentProfile student, String key, String purpose) {
        MediaAsset asset = new MediaAsset();
        asset.setUserId(user.getId()); asset.setStudentId(student.getId());
        asset.setStorageProvider("COS"); asset.setBucket("test-bucket"); asset.setRegion("ap-beijing");
        asset.setObjectKey(key); asset.setMimeType("image/png"); asset.setFormat("png");
        asset.setWidth(1200); asset.setHeight(800); asset.setSizeBytes(1024L);
        asset.setChecksumSha256("a".repeat(64)); asset.setPurpose(purpose); asset.setStatus("AVAILABLE");
        em.persist(asset); em.flush(); return asset;
    }

    @Test void allPersistentEntitiesHaveValidIdentifiersAndTables() {
        assertEquals(36, em.getMetamodel().getEntities().size());
        em.getMetamodel().getEntities().forEach(entity -> {
            assertNotNull(entity.getIdType(), entity.getName());
            // SELECT forces Hibernate to resolve every table/column, not just Java annotations.
            em.createQuery("select e from " + entity.getName() + " e").setMaxResults(1).getResultList();
        });
    }

    @Test void captureProcessingAndPrintingRoundTripKeepsVersionsAndSnapshots() throws Exception {
        User user = user(); StudentProfile student = student(user, "星星");
        UserIdentity identity = new UserIdentity(); identity.setUserId(user.getId());
        identity.setProvider("WECHAT_MINIAPP"); identity.setAppId("test-app");
        identity.setOpenid(UUID.randomUUID().toString()); identity.setStatus("BOUND"); em.persist(identity);
        UserProfile profile = new UserProfile(); profile.setUserId(user.getId());
        profile.setLastStudentId(student.getId()); em.persist(profile);
        MediaAsset original = asset(user, student, UUID.randomUUID()+".png", "ORIGINAL");
        MediaAsset processed = asset(user, student, UUID.randomUUID()+".png", "PROCESSED");
        CaptureBatch batch = new CaptureBatch(); batch.setUserId(user.getId()); batch.setStudentId(student.getId());
        batch.setMode("MULTI"); batch.setClientRequestId(UUID.randomUUID().toString()); em.persist(batch); em.flush();
        CapturePhoto photo = new CapturePhoto(); photo.setUserId(user.getId()); photo.setStudentId(student.getId());
        photo.setBatchId(batch.getId()); photo.setOriginalAssetId(original.getId()); photo.setSortOrder(0);
        photo.setSource("CAMERA"); photo.setUploadedAt(Instant.now()); em.persist(photo); em.flush();
        ImageRevision input = new ImageRevision(); input.setUserId(user.getId()); input.setStudentId(student.getId());
        input.setPhotoId(photo.getId()); input.setAssetId(original.getId()); input.setOperation("ORIGINAL"); em.persist(input); em.flush();
        photo.setCurrentRevisionId(input.getId());
        ProcessingJob job = new ProcessingJob(); job.setUserId(user.getId()); job.setStudentId(student.getId());
        job.setBatchId(batch.getId()); job.setOperation("ERASE"); job.setApplyScope("CURRENT");
        job.setParametersJson("{\"schema_version\":1}"); job.setRequestKey(UUID.randomUUID().toString());
        job.setRequestHash("b".repeat(64)); job.setTraceId("trace-test"); em.persist(job); em.flush();
        ProcessingJobItem item = new ProcessingJobItem(); item.setJobId(job.getId()); item.setPhotoId(photo.getId());
        item.setInputRevisionId(input.getId()); em.persist(item); em.flush();
        ImageRevision output = new ImageRevision(); output.setUserId(user.getId()); output.setStudentId(student.getId());
        output.setPhotoId(photo.getId()); output.setParentRevisionId(input.getId()); output.setAssetId(processed.getId());
        output.setOperation("ERASE"); output.setJobItemId(item.getId()); em.persist(output); em.flush();
        item.setOutputRevisionId(output.getId()); item.setStatus("SUCCEEDED"); photo.setCurrentRevisionId(output.getId());
        AiCallLog log = new AiCallLog(); log.setUserId(user.getId()); log.setPhone(user.getPhone());
        log.setStudentId(student.getId()); log.setAiType("ERASE"); log.setSuccess(true);
        log.setProvider("TENCENT_OCR"); log.setApiAction("EraseHandwrittenImageOCR"); log.setRequestId("tencent-request");
        log.setJobItemId(item.getId()); log.setInputAssetId(original.getId()); log.setOutputAssetId(processed.getId());
        em.persist(log);
        PrintTemplateCategory category = new PrintTemplateCategory(); category.setCode(UUID.randomUUID().toString().substring(0, 8));
        category.setName("A4"); em.persist(category); em.flush();
        PrintTemplate template = new PrintTemplate(); template.setCategoryId(category.getId());
        template.setCode(UUID.randomUUID().toString()); template.setName("双题模板"); em.persist(template); em.flush();
        PrintTemplateVersion version = new PrintTemplateVersion(); version.setTemplateId(template.getId()); version.setVersionNo(1);
        version.setPaperWidthMm(new BigDecimal("210.00")); version.setPaperHeightMm(new BigDecimal("297.00"));
        version.setOrientation("PORTRAIT"); version.setSlotsPerPage(2); version.setLayoutJson("{\"schema_version\":1,\"slots\":2}");
        version.setRendererType("HTML_CSS"); version.setRendererVersion("1"); version.setCodeContent("<div>{{image}}</div>");
        version.setCodeHash("c".repeat(64)); em.persist(version); em.flush();
        template.setCurrentVersionId(version.getId());
        PrintTask task = new PrintTask(); task.setUserId(user.getId()); task.setStudentId(student.getId());
        task.setTemplateVersionId(version.getId()); task.setTemplateSnapshotJson("{\"name\":\"双题模板\"}");
        task.setSourceType("CAPTURE"); task.setItemCount(1); task.setClientRequestId(UUID.randomUUID().toString());
        task.setRequestHash("d".repeat(64)); em.persist(task); em.flush();
        PrintTaskItem printItem = new PrintTaskItem(); printItem.setTaskId(task.getId()); printItem.setSortOrder(0);
        printItem.setSourcePhotoId(photo.getId()); printItem.setImageAssetId(processed.getId());
        printItem.setContentSnapshotJson("{\"student\":\"星星\"}"); em.persist(printItem); em.flush();
        String photoId = photo.getId(), taskId = task.getId(), itemId = printItem.getId();
        student.setGrade(6); template.setName("新的模板名称"); em.flush(); em.clear();
        CapturePhoto found = em.find(CapturePhoto.class, photoId);
        assertFalse(Hibernate.isInitialized(found.getOriginalAssetRef()));
        assertEquals(original.getId(), found.getOriginalAssetRef().getId());
        assertEquals(input.getId(), found.getCurrentRevisionRef().getParentRevisionRef().getId());
        assertEquals("星星", mapper.readTree(em.find(PrintTaskItem.class, itemId).getContentSnapshotJson()).path("student").asText());
        assertTrue(em.find(PrintTask.class, taskId).getTemplateSnapshotJson().contains("双题模板"));
        assertEquals("1", em.createNativeQuery("select json_unquote(json_extract(layout_json, '$.schema_version')) from print_template_version where id=:id")
                .setParameter("id", version.getId()).getSingleResult());
        assertNotNull(em.find(PrintTask.class, taskId).getCreatedAt());
        assertNull(log.getInputTokens()); // OCR does not invent token usage.
    }

    @Test void twoStudentsCanUseSameSubjectWithoutBreakingLegacyRows() {
        User user = user(); StudentProfile a = student(user, "一"), b = student(user, "二");
        for (String studentId : new String[]{null, a.getId(), b.getId()}) {
            UserSubject subject = new UserSubject(); subject.setUserId(user.getId()); subject.setStudentId(studentId);
            subject.setName("数学"); subject.setNormalizedName("数学"); subject.setSystemKey("MATH"); em.persist(subject);
        }
        em.flush(); em.clear();
        assertEquals(3L, em.createQuery("select count(s) from UserSubject s where s.userId=:id", Long.class).setParameter("id", user.getId()).getSingleResult());
    }

    @Test void oldBookAndAiLogCanStillPersistWithoutStudentBackfill() {
        User user = user(); BookEntry entry = new BookEntry(); entry.setId(UUID.randomUUID().toString());
        entry.setUserId(user.getId()); entry.setGrade(5); entry.setSubject("数学"); entry.setErrorType("马虎");
        entry.setCreatedAt(Instant.now()); entry.setWidth(80); entry.setHeight(80); entry.setObjectKey("book/legacy.zip");
        em.persist(entry); em.flush(); em.clear();
        BookEntry found = em.find(BookEntry.class, entry.getId());
        assertNull(found.getStudentId()); assertNotNull(found.getVersion());
        assertEquals("book/legacy.zip", found.getObjectKey());
        found.setRemark("更新仍兼容旧记录"); em.flush();
        assertTrue(found.getVersion() > 0);
    }

    @Test void studentPreferenceIsIndependentFromLegacyAccountPreference() {
        User user = user(); StudentProfile a = student(user, "一"), b = student(user, "二");
        UserTaxonomyPref legacy = new UserTaxonomyPref(); legacy.setUserId(user.getId()); em.persist(legacy);
        for (StudentProfile student : new StudentProfile[]{a, b}) {
            StudentTaxonomyPref pref = new StudentTaxonomyPref(); pref.setUserId(user.getId()); pref.setStudentId(student.getId()); em.persist(pref);
        }
        em.flush(); em.clear();
        assertNotNull(em.find(UserTaxonomyPref.class, user.getId()));
        assertEquals(2L, em.createQuery("select count(p) from StudentTaxonomyPref p where p.userId=:id",Long.class).setParameter("id",user.getId()).getSingleResult());
    }

    @Test void sameStudentCannotCreateDuplicateSubject() {
        User user = user(); StudentProfile student = student(user, "一");
        UserSubject first = new UserSubject(); first.setUserId(user.getId()); first.setStudentId(student.getId());
        first.setName("数学"); first.setNormalizedName("数学"); em.persist(first); em.flush();
        UserSubject duplicate = new UserSubject(); duplicate.setUserId(user.getId()); duplicate.setStudentId(student.getId());
        duplicate.setName("数学"); duplicate.setNormalizedName("数学");
        assertThrows(jakarta.persistence.PersistenceException.class, () -> { em.persist(duplicate); em.flush(); });
    }

    @Test void identicalCosLocatorCannotRegisterTwoAssets() {
        User user = user(); StudentProfile student = student(user, "一");
        String key = UUID.randomUUID()+".png";
        asset(user, student, key, "ORIGINAL");
        assertThrows(jakarta.persistence.PersistenceException.class, () -> asset(user, student, key, "PROCESSED"));
    }

    @Test void invalidStudentGradeIsRejectedByMysql() {
        User user = user(); StudentProfile student = new StudentProfile();
        student.setUserId(user.getId()); student.setNickname("非法年级"); student.setGrade(13); em.persist(student);
        assertThrows(jakarta.persistence.PersistenceException.class, () -> em.flush());
    }
}
