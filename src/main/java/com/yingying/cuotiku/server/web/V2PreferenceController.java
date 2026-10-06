package com.yingying.cuotiku.server.web;

import com.yingying.cuotiku.server.dto.ApiResponse;
import com.yingying.cuotiku.server.dto.TaxonomyDto.PreferenceDto;
import com.yingying.cuotiku.server.dto.TaxonomyDto.PreferenceRequest;
import com.yingying.cuotiku.server.entity.SubjectTopic;
import com.yingying.cuotiku.server.entity.User;
import com.yingying.cuotiku.server.entity.UserSubject;
import com.yingying.cuotiku.server.entity.UserTaxonomyPref;
import com.yingying.cuotiku.server.repository.SubjectTopicRepository;
import com.yingying.cuotiku.server.repository.UserSubjectRepository;
import com.yingying.cuotiku.server.repository.UserTaxonomyPrefRepository;
import com.yingying.cuotiku.server.security.AuthenticatedUser;
import com.yingying.cuotiku.server.service.SubjectTopicService;
import com.yingying.cuotiku.server.web.ApiException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 账号默认选择（CONTRACT §5.7）：服务端唯一来源；读写均不触发激活。
 */
@RestController
@RequestMapping("/api/v2/preferences")
public class V2PreferenceController {

    private final UserTaxonomyPrefRepository prefRepository;
    private final UserSubjectRepository subjectRepository;
    private final SubjectTopicRepository topicRepository;
    private final SubjectTopicService subjectTopicService;

    public V2PreferenceController(UserTaxonomyPrefRepository prefRepository,
                                  UserSubjectRepository subjectRepository,
                                  SubjectTopicRepository topicRepository,
                                  SubjectTopicService subjectTopicService) {
        this.prefRepository = prefRepository;
        this.subjectRepository = subjectRepository;
        this.topicRepository = topicRepository;
        this.subjectTopicService = subjectTopicService;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public ApiResponse<PreferenceDto> get(@AuthenticationPrincipal AuthenticatedUser principal) {
        UserTaxonomyPref pref = prefRepository.findById(principal.user().getId()).orElse(null);
        return ApiResponse.ok(new PreferenceDto(
                pref == null ? null : pref.getDefaultSubjectId(),
                pref == null ? null : pref.getDefaultTopicId()));
    }

    @PutMapping
    @Transactional
    public ApiResponse<PreferenceDto> put(@RequestBody PreferenceRequest request,
                                          @AuthenticationPrincipal AuthenticatedUser principal) {
        User user = principal.user();
        subjectTopicService.ensureWriteAllowed(user);
        Long subjectId = request.defaultSubjectId();
        Long topicId = request.defaultTopicId();
        if (subjectId != null) {
            UserSubject subject = subjectRepository.findByUserIdAndId(user.getId(), subjectId)
                    .orElseThrow(() -> ApiException.notFound("分类不存在"));
            if (!UserSubject.STATUS_ACTIVE.equals(subject.getStatus())) {
                throw ApiException.badRequest("默认科目已停用，请重新选择");
            }
        }
        if (topicId != null) {
            if (subjectId == null) {
                throw ApiException.badRequest("默认主题必须同时指定所属科目");
            }
            SubjectTopic topic = topicRepository.findByUserIdAndId(user.getId(), topicId)
                    .orElseThrow(() -> ApiException.notFound("分类不存在"));
            if (!topic.getSubjectId().equals(subjectId)) {
                throw ApiException.badRequest("默认主题不属于默认科目");
            }
            if (!UserSubject.STATUS_ACTIVE.equals(topic.getStatus())) {
                throw ApiException.badRequest("默认主题已停用，请重新选择");
            }
        }
        UserTaxonomyPref pref = prefRepository.findById(user.getId()).orElseGet(() -> {
            UserTaxonomyPref created = new UserTaxonomyPref();
            created.setUserId(user.getId());
            return created;
        });
        pref.setDefaultSubjectId(subjectId);
        pref.setDefaultTopicId(topicId);
        prefRepository.save(pref);
        return ApiResponse.ok(new PreferenceDto(subjectId, topicId));
    }
}
