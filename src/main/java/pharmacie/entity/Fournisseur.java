package pharmacie.entity;

import java.util.HashSet;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import jakarta.validation.constraints.Size;

import lombok.*;

@Entity
@Getter
@Setter
@NoArgsConstructor
@RequiredArgsConstructor
@ToString
public class Fournisseur {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Setter(AccessLevel.NONE) // la clé est auto-générée par la BD
    private Long id;

    @NonNull
    @Column(unique = true, length = 255)
    private String nom;

    @Size(max = 255)
    @Column(length = 255)
    private String email;

    @Size(max = 20)
    @Column(length = 20)
    private String telephone;

    // Côté inverse de la relation ManyToMany définie dans Categorie
    @ManyToMany(mappedBy = "fournisseurs")
    @ToString.Exclude
    @JsonIgnoreProperties({ "fournisseurs", "medicaments" })
    private Set<Categorie> categories = new HashSet<>();
}
