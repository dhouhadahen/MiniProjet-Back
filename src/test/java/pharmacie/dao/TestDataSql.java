package pharmacie.dao;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import pharmacie.entity.*;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Ce test vérifie que le fichier data.sql a bien chargé toutes les données
 * dans la base H2 au démarrage.
 * On vérifie le nombre d'enregistrements et quelques valeurs précises.
 */
@DataJpaTest
class TestDataSql {

    @Autowired
    private CategorieRepository categorieRepository;
    @Autowired
    private MedicamentRepository medicamentRepository;
    @Autowired
    private DispensaireRepository dispensaireRepository;
    @Autowired
    private CommandeRepository commandeRepository;
    @Autowired
    private LigneRepository ligneRepository;
    @Autowired
    private FournisseurRepository fournisseurRepository;

    // ========== Tests sur les catégories ==========

    @Test
    void onDoitAvoir10Categories() {
        long nbCategories = categorieRepository.count();
        assertEquals(10, nbCategories, "data.sql doit insérer exactement 10 catégories");
    }

    @Test
    void laPremiereCategorieSAppelleAntalgiques() {
        // On récupère la catégorie avec le code 1
        Categorie cat = categorieRepository.findById(1).orElseThrow();
        assertEquals("Antalgiques et Antipyrétiques", cat.getLibelle());
        assertNotNull(cat.getDescription(), "La catégorie doit avoir une description");
    }

    @Test
    void onPeutChercherUneCategorieParLibelle() {
        Categorie cat = categorieRepository.findByLibelle("Antibiotiques");
        assertNotNull(cat, "On doit trouver la catégorie Antibiotiques");
        assertEquals(3, cat.getCode(), "Antibiotiques doit avoir le code 3");
    }

    // ========== Tests sur les médicaments ==========

    @Test
    void onDoitAvoir100Medicaments() {
        long nbMedicaments = medicamentRepository.count();
        assertEquals(100, nbMedicaments, "data.sql doit insérer exactement 100 médicaments");
    }

    @Test
    void chaqueCategorieDoitAvoir10Medicaments() {
        // On vérifie que chaque catégorie a bien 10 médicaments
        List<Categorie> categories = categorieRepository.findAll();
        for (Categorie cat : categories) {
            int nbMeds = cat.getMedicaments().size();
            assertEquals(10, nbMeds,
                    "La catégorie '" + cat.getLibelle() + "' doit avoir 10 médicaments");
        }
    }

    @Test
    void leParacetamolEstDansLaBonneCategorie() {
        // Le premier médicament inséré est "Paracétamol 500mg" dans la catégorie 1
        Medicament med = medicamentRepository.findById(1).orElseThrow();
        assertEquals("Paracétamol 500mg", med.getNom());
        assertEquals(1, med.getCategorie().getCode(), "Le Paracétamol doit être dans la catégorie 1");
        assertEquals(0, med.getPrixUnitaire().compareTo(new java.math.BigDecimal("2.50")), "Le prix doit être 2.50");
        assertFalse(med.isIndisponible(), "Le Paracétamol ne doit pas être indisponible");
    }

    @Test
    void lesMedicamentsOntUneUrlImage() {
        // On vérifie que les médicaments ont bien une URL d'image
        Medicament med = medicamentRepository.findById(1).orElseThrow();
        assertNotNull(med.getUrlImage(), "Le médicament doit avoir une urlImage");
        assertTrue(med.getUrlImage().startsWith("https://"), "L'URL de l'image doit commencer par https://");
    }

    // ========== Tests sur les fournisseurs ==========

    @Test
    void onDoitAvoir10Fournisseurs() {
        long nbFournisseurs = fournisseurRepository.count();
        assertEquals(10, nbFournisseurs, "data.sql doit insérer exactement 10 fournisseurs");
    }

    @Test
    void lePremierFournisseurEstPfizerAfrique() {
        Fournisseur f = fournisseurRepository.findByNom("Pfizer Afrique");
        assertNotNull(f, "On doit trouver le fournisseur Pfizer Afrique");
        assertEquals("contact@pfizer-afrique.com", f.getEmail());
    }

    @Test
    void lesFournisseursSontReliesAuxCategories() {
        // Pfizer Afrique doit fournir au moins une catégorie (via la table de jointure)
        Fournisseur pfizer = fournisseurRepository.findByNom("Pfizer Afrique");
        assertNotNull(pfizer);
        assertFalse(pfizer.getCategories().isEmpty(),
                "Pfizer Afrique doit être relié à au moins une catégorie");
    }

    // ========== Tests sur les dispensaires ==========

    @Test
    void onDoitAvoir10Dispensaires() {
        long nbDispensaires = dispensaireRepository.count();
        assertEquals(10, nbDispensaires, "data.sql doit insérer exactement 10 dispensaires");
    }

    @Test
    void lePremierDispensaireEstADakar() {
        Dispensaire d = dispensaireRepository.findById("DSP01").orElseThrow();
        assertEquals("Dispensaire Central Dakar", d.getNom());
        assertEquals("Dakar", d.getAdresse().getVille());
        assertEquals("Sénégal", d.getAdresse().getPays());
    }

    // ========== Tests sur les commandes ==========

    @Test
    void onDoitAvoir8Commandes() {
        long nbCommandes = commandeRepository.count();
        assertEquals(8, nbCommandes, "data.sql doit insérer exactement 8 commandes");
    }

    @Test
    void deuxCommandesNeSontPasEncoreEnvoyees() {
        // Les commandes 5 et 7 ont envoyeele = NULL dans data.sql
        List<Commande> toutes = commandeRepository.findAll();

        int nbPasEnvoyees = 0;
        for (Commande c : toutes) {
            if (c.getEnvoyeele() == null) {
                nbPasEnvoyees++;
            }
        }
        assertEquals(2, nbPasEnvoyees, "Il doit y avoir exactement 2 commandes pas encore envoyées");
    }

    @Test
    void lesCommandesSontRelieesAuxDispensaires() {
        // La commande 1 doit être liée au dispensaire DSP01
        Commande cmd = commandeRepository.findById(1).orElseThrow();
        assertNotNull(cmd.getDispensaire(), "La commande doit avoir un dispensaire");
        assertEquals("DSP01", cmd.getDispensaire().getCode());
    }

    // ========== Tests sur les lignes de commande ==========

    @Test
    void chaqueCommandeADesLignes() {
        // On vérifie que chaque commande a au moins une ligne
        List<Commande> commandes = commandeRepository.findAll();
        for (Commande cmd : commandes) {
            assertFalse(cmd.getLignes().isEmpty(),
                    "La commande " + cmd.getNumero() + " doit avoir au moins une ligne");
        }
    }

    @Test
    void lesLignesReferencentDesMedicamentsExistants() {
        // On vérifie que les lignes pointent vers des médicaments qui existent
        List<Ligne> lignes = ligneRepository.findAll();
        assertFalse(lignes.isEmpty(), "Il doit y avoir des lignes de commande");

        for (Ligne l : lignes) {
            assertNotNull(l.getMedicament(), "Chaque ligne doit référencer un médicament");
            assertNotNull(l.getCommande(), "Chaque ligne doit référencer une commande");
            assertTrue(l.getQuantite() > 0, "La quantité doit être positive");
        }
    }
}
