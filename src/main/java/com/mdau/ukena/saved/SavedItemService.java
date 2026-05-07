package com.mdau.ukena.saved;

import com.mdau.ukena.common.ApiException;
import com.mdau.ukena.product.Product;
import com.mdau.ukena.product.ProductRepository;
import com.mdau.ukena.product.ProductStatus;
import com.mdau.ukena.security.CurrentUser;
import com.mdau.ukena.user.User;
import com.mdau.ukena.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SavedItemService {

    private final SavedItemRepository savedItemRepository;
    private final ProductRepository   productRepository;
    private final UserRepository      userRepository;

    @Transactional
    public SavedItemDto save(String productId, CurrentUser currentUser) {
        if (savedItemRepository.existsByUserIdAndProductId(currentUser.id(), productId)) {
            throw ApiException.conflict("Product is already in your saved items");
        }

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> ApiException.notFound("Product not found"));

        if (product.getStatus() == ProductStatus.SUSPENDED_BY_ADMIN
                || product.isDeleted()) {
            throw ApiException.badRequest("This product is no longer available");
        }

        User user = userRepository.findById(currentUser.id())
                .orElseThrow(() -> ApiException.notFound("User not found"));

        SavedItem saved = savedItemRepository.save(SavedItem.builder()
                .user(user)
                .product(product)
                .build());

        return toDto(saved);
    }

    @Transactional(readOnly = true)
    public List<SavedItemDto> listSaved(CurrentUser currentUser) {
        return savedItemRepository
                .findByUserIdWithProduct(currentUser.id())
                .stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional
    public void unsave(String productId, CurrentUser currentUser) {
        SavedItem item = savedItemRepository
                .findByUserIdAndProductId(currentUser.id(), productId)
                .orElseThrow(() -> ApiException.notFound("Saved item not found"));
        savedItemRepository.delete(item);
    }

    private SavedItemDto toDto(SavedItem s) {
        Product p = s.getProduct();
        String craftType = p.getCreator() != null ? p.getCreator().getCraft() : null;
        return new SavedItemDto(
                s.getId().toString(),
                p.getId(),
                p.getName(),
                p.getHeroImage(),
                p.getPricePence(),
                craftType,
                p.getCreator() != null ? p.getCreator().getFullName() : null,
                s.getSavedAt()
        );
    }
}