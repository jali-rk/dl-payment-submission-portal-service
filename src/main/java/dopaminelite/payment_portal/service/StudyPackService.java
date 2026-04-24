package dopaminelite.payment_portal.service;

import dopaminelite.payment_portal.dto.common.PaginatedResponse;
import dopaminelite.payment_portal.dto.studypack.StudyPackCreateRequest;
import dopaminelite.payment_portal.dto.studypack.StudyPackResponse;
import dopaminelite.payment_portal.dto.studypack.StudyPackUpdateRequest;
import dopaminelite.payment_portal.entity.StudyPack;
import dopaminelite.payment_portal.entity.enums.PurchaseStatus;
import dopaminelite.payment_portal.mapper.StudyPackMapper;
import dopaminelite.payment_portal.repository.StudyPackPurchaseRepository;
import dopaminelite.payment_portal.repository.StudyPackRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

/**
 * Service for managing study pack business logic.
 * Handles study pack creation, retrieval, and filtering.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StudyPackService {

    private final StudyPackRepository studyPackRepository;
    private final StudyPackPurchaseRepository studyPackPurchaseRepository;
    private final StudyPackMapper studyPackMapper;
    private final FileUploadService fileUploadService;

    /**
     * Creates a new study pack.
     *
     * @param request the study pack creation request
     * @param thumbnail optional thumbnail image file
     * @return the created study pack
     */
    @Transactional
    public StudyPackResponse createStudyPack(StudyPackCreateRequest request, MultipartFile thumbnail) {
        StudyPack studyPack = new StudyPack();
        studyPack.setName(request.getName());
        studyPack.setDescription(request.getDescription());
        studyPack.setPrice(request.getPrice());
        studyPack.setClassIds(request.getClassIds());
        studyPack.setIsActive(request.getIsActive() != null ? request.getIsActive() : true);
        
        // Upload thumbnail if provided
        if (thumbnail != null && !thumbnail.isEmpty()) {
            String thumbnailUrl = fileUploadService.uploadFile(thumbnail, "study-packs");
            studyPack.setThumbnailUrl(thumbnailUrl);
        }
        
        StudyPack savedStudyPack = studyPackRepository.save(studyPack);
        return studyPackMapper.toResponse(savedStudyPack);
    }

    /**
     * Retrieves a paginated list of study packs with optional filtering by active status.
     *
     * @param isActive filter by active status, null for no filtering
     * @param limit maximum number of results per page
     * @param offset number of results to skip
     * @return paginated response containing study pack list and total count
     */
    public PaginatedResponse<StudyPackResponse> listStudyPacks(
            Boolean isActive,
            int limit,
            int offset
    ) {
        Pageable pageable = PageRequest.of(offset / limit, limit, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<StudyPack> studyPackPage;
        
        if (isActive != null) {
            studyPackPage = studyPackRepository.findByIsActive(isActive, pageable);
        } else {
            studyPackPage = studyPackRepository.findAll(pageable);
        }
        
        List<StudyPackResponse> items = studyPackPage.getContent()
                .stream()
                .map(studyPackMapper::toResponse)
                .toList();
        
        long total = studyPackPage.getTotalElements();
        
        return new PaginatedResponse<>(items, total);
    }

    /**
     * Updates an existing study pack.
     *
     * @param id the study pack ID
     * @param request the update request
     * @param thumbnail optional new thumbnail image file
     * @return the updated study pack
     */
    @Transactional
    public StudyPackResponse updateStudyPack(UUID id, StudyPackUpdateRequest request, MultipartFile thumbnail) {
        StudyPack studyPack = studyPackRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Study pack not found with ID: " + id));
        
        studyPack.setName(request.getName());
        studyPack.setDescription(request.getDescription());
        studyPack.setPrice(request.getPrice());
        studyPack.setClassIds(request.getClassIds());
        
        if (request.getIsActive() != null) {
            studyPack.setIsActive(request.getIsActive());
        }
        
        // Upload new thumbnail if provided
        if (thumbnail != null && !thumbnail.isEmpty()) {
            String thumbnailUrl = fileUploadService.uploadFile(thumbnail, "study-packs");
            studyPack.setThumbnailUrl(thumbnailUrl);
        }
        
        StudyPack updatedStudyPack = studyPackRepository.save(studyPack);
        return studyPackMapper.toResponse(updatedStudyPack);
    }

    /**
     * Deletes a study pack. Performs soft delete if students have purchased it.
     *
     * @param id the study pack ID
     */
    @Transactional
    public void deleteStudyPack(UUID id) {
        StudyPack studyPack = studyPackRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Study pack not found with ID: " + id));
        
        // Check if any students have purchased this study pack
        long purchaseCount = studyPackPurchaseRepository.findAll().stream()
                .filter(purchase -> purchase.getStudyPack() != null && 
                                  purchase.getStudyPack().getId().equals(id))
                .count();
        
        if (purchaseCount > 0) {
            // Soft delete: mark as inactive instead of deleting
            studyPack.setIsActive(false);
            studyPackRepository.save(studyPack);
        } else {
            // Hard delete: no purchases exist
            studyPackRepository.delete(studyPack);
        }
    }
    
}
