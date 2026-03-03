package pharmacie.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import pharmacie.dao.*;
import pharmacie.entity.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Ce test vérifie la logique de réapprovisionnement :
 * - trouver les médicaments dont le stock est en dessous du seuil
 * - trouver les fournisseurs correspondants via les catégories
 * - grouper les médicaments par fournisseur
 *
 * On ne teste pas l'envoi de mails ici (c'est un test unitaire, pas un test
 * d'intégration).
 * On vérifie juste que la logique de détection et de regroupement fonctionne.
 */
@DataJpaTest
class TestReapprovisionnement {

    @Autowired
    private MedicamentRepository medicamentRepository;
    @Autowired
    private FournisseurRepository fournisseurRepository;

    // ========== Détection des médicaments à réapprovisionner ==========

    @Test
    void detecterLesMedicamentsEnStockFaible() {
        // On met un médicament en dessous de son seuil de réappro
        Medicament med = medicamentRepository.findById(1).orElseThrow();
        med.setUnitesEnStock(5); // stock très bas
        med.setNiveauDeReappro(50); // seuil élevé
        medicamentRepository.saveAndFlush(med);

        // On cherche tous les médicaments à réapprovisionner
        List<Medicament> tous = medicamentRepository.findAll();
        List<Medicament> aReappro = new ArrayList<>();
        for (Medicament m : tous) {
            if (m.getNiveauDeReappro() != null && m.getUnitesEnStock() != null) {
                if (m.getUnitesEnStock() < m.getNiveauDeReappro()) {
                    aReappro.add(m);
                }
            }
        }

        // Le médicament qu'on a modifié doit être dans la liste
        boolean medTrouve = false;
        for (Medicament m : aReappro) {
            if (m.getReference().equals(med.getReference())) {
                medTrouve = true;
                break;
            }
        }
        assertTrue(medTrouve, "Le médicament avec stock faible doit être détecté");
    }

    @Test
    void pasDeMedicamentAReapprovisionnerSiStockSuffisant() {
        // On met tous les médicaments avec un stock bien au-dessus du seuil
        List<Medicament> tous = medicamentRepository.findAll();
        for (Medicament m : tous) {
            m.setUnitesEnStock(1000);
            m.setNiveauDeReappro(10);
        }
        medicamentRepository.saveAll(tous);
        medicamentRepository.flush();

        // Aucun médicament ne devrait être à réapprovisionner
        List<Medicament> aReappro = new ArrayList<>();
        for (Medicament m : medicamentRepository.findAll()) {
            if (m.getUnitesEnStock() < m.getNiveauDeReappro()) {
                aReappro.add(m);
            }
        }

        assertTrue(aReappro.isEmpty(), "Aucun médicament ne devrait être à réapprovisionner");
    }

    // ========== Relations Fournisseur <-> Catégorie <-> Médicament ==========

    @Test
    void trouverLesFournisseursDUnMedicament() {
        // Le médicament 1 est dans la catégorie 1 (Antalgiques)
        // La catégorie 1 a des fournisseurs reliés (via data.sql : Pfizer, Sanofi,
        // Médis)
        Medicament med = medicamentRepository.findById(1).orElseThrow();
        Categorie cat = med.getCategorie();

        // On récupère les fournisseurs de cette catégorie
        Set<Fournisseur> fournisseurs = cat.getFournisseurs();

        assertFalse(fournisseurs.isEmpty(),
                "La catégorie du médicament doit avoir au moins un fournisseur");
    }

    @Test
    void lesFournisseursSontReliesAuxBonnesCategories() {
        // On vérifie que chaque fournisseur a au moins une catégorie
        List<Fournisseur> fournisseurs = fournisseurRepository.findAll();

        int nbAvecCategories = 0;
        for (Fournisseur f : fournisseurs) {
            if (!f.getCategories().isEmpty()) {
                nbAvecCategories++;
            }
        }

        assertTrue(nbAvecCategories > 0,
                "Au moins un fournisseur doit être relié à une catégorie");
    }

    @Test
    void trouverFournisseursPourMedicamentsParRequeteJPQL() {
        // On utilise la requête custom du FournisseurRepository
        // Le médicament 1 est dans la catégorie 1
        List<Fournisseur> fournisseurs = fournisseurRepository.fournisseursPourMedicaments(List.of(1));

        assertFalse(fournisseurs.isEmpty(),
                "On doit trouver des fournisseurs pour le médicament 1");

        // On vérifie qu'il n'y a pas de doublons
        long nbDistincts = fournisseurs.stream().map(Fournisseur::getId).distinct().count();
        assertEquals(fournisseurs.size(), nbDistincts,
                "La requête ne doit pas renvoyer de doublons (DISTINCT)");
    }

    @Test
    void trouverFournisseursAvecCategoriesPreChargees() {
        // On utilise la requête avec JOIN FETCH
        List<Fournisseur> fournisseurs = fournisseurRepository
                .fournisseursAvecCategoriesPourMedicaments(List.of(1, 11, 21));

        assertFalse(fournisseurs.isEmpty(),
                "On doit trouver des fournisseurs pour ces médicaments");

        // Chaque fournisseur doit avoir ses catégories pré-chargées (pas de
        // LazyInitializationException)
        for (Fournisseur f : fournisseurs) {
            assertFalse(f.getCategories().isEmpty(),
                    "Les catégories du fournisseur " + f.getNom() + " doivent être chargées");
        }
    }

    // ========== Simulation du regroupement par fournisseur ==========

    @Test
    void grouperLesMedicamentsParFournisseur() {
        // On simule la logique du ReapprovisionnementService
        // On met un médicament en stock faible
        Medicament med = medicamentRepository.findById(1).orElseThrow();
        med.setUnitesEnStock(3);
        med.setNiveauDeReappro(50);
        medicamentRepository.saveAndFlush(med);

        // On récupère les fournisseurs de sa catégorie
        Set<Fournisseur> fournisseurs = med.getCategorie().getFournisseurs();
        assertFalse(fournisseurs.isEmpty(), "Le médicament doit avoir des fournisseurs");

        // Chaque fournisseur devrait recevoir un mail avec ce médicament
        for (Fournisseur f : fournisseurs) {
            assertNotNull(f.getNom(), "Le fournisseur doit avoir un nom");
            // En vrai, le service envoie un email ici
            // On vérifie juste que les données nécessaires sont présentes
            System.out.println("Fournisseur : " + f.getNom()
                    + " -> Médicament à réappro : " + med.getNom()
                    + " (stock=" + med.getUnitesEnStock()
                    + ", seuil=" + med.getNiveauDeReappro() + ")");
        }
    }
}
