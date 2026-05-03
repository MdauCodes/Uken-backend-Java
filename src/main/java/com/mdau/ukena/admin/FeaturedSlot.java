package com.mdau.ukena.admin;

import com.mdau.ukena.creator.Creator;
import com.mdau.ukena.product.Product;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "featured_slots")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class FeaturedSlot {

    @Id
    @Column(name = "position")
    private int position; // 1-4

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "creator_id")
    private Creator creator;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    private Product product;
}