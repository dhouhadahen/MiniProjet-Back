package pharmacie.dao;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import pharmacie.entity.Fournisseur;

// Auto-implémenté par Spring
public interface FournisseurRepository extends JpaRepository<Fournisseur, Long> {

    /**
     * Recherche un fournisseur par son nom (unique)
     */
    Fournisseur findByNom(String nom);

    /**
     * Trouve les fournisseurs susceptibles de réapprovisionner des médicaments
     * à partir de leurs références.
     * La relation passe par les catégories (un fournisseur fournit des catégories).
     */
    @Query("""
                SELECT DISTINCT f FROM Fournisseur f
                JOIN f.categories c
                JOIN c.medicaments m
                WHERE m.reference IN :references
            """)
    List<Fournisseur> fournisseursPourMedicaments(List<Integer> references);

    /**
     * Trouve les fournisseurs avec leurs catégories pré-chargées
     * pour les médicaments donnés par leurs références.
     */
    @Query("""
                SELECT DISTINCT f FROM Fournisseur f
                JOIN FETCH f.categories c
                WHERE c IN (SELECT m.categorie FROM Medicament m WHERE m.reference IN :references)
            """)
    List<Fournisseur> fournisseursAvecCategoriesPourMedicaments(List<Integer> references);
}
