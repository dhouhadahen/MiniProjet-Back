package pharmacie.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import pharmacie.dao.*;
import pharmacie.entity.*;

import java.util.List;
import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Ce test vérifie la logique métier de création de commande.
 * On utilise le CommandeService pour tester :
 * - la création d'une commande pour un dispensaire
 * - l'ajout de lignes de commande
 * - l'expédition d'une commande
 * - les cas d'erreur (dispensaire inexistant, stock insuffisant, etc.)
 */
@DataJpaTest
class TestCreationCommande {

    @Autowired
    private CommandeRepository commandeRepository;
    @Autowired
    private MedicamentRepository medicamentRepository;
    @Autowired
    private DispensaireRepository dispensaireRepository;
    @Autowired
    private LigneRepository ligneRepository;

    // On crée le service manuellement pour éviter un conflit de contexte Spring
    private CommandeService commandeService;

    @BeforeEach
    void setUp() {
        commandeService = new CommandeService(
                commandeRepository, dispensaireRepository, ligneRepository, medicamentRepository);
    }

    // ========== Création de commande ==========

    @Test
    void creerUneCommandePourUnDispensaireExistant() {
        // On crée une commande pour le dispensaire DSP01 (qui existe dans data.sql)
        Commande cmd = commandeService.creerCommande("DSP01");

        // La commande doit être créée avec un numéro auto-généré
        assertNotNull(cmd.getNumero(), "La commande doit avoir un numéro");
        // Elle doit être liée au bon dispensaire
        assertEquals("DSP01", cmd.getDispensaire().getCode());
        // Elle ne doit pas encore être envoyée
        assertNull(cmd.getEnvoyeele(), "La commande ne doit pas encore être envoyée");
        // L'adresse de livraison doit être initialisée avec l'adresse du dispensaire
        assertNotNull(cmd.getAdresseLivraison(), "L'adresse de livraison doit être initialisée");
    }

    @Test
    void creerUneCommandePourUnDispensaireInexistant() {
        // Si le dispensaire n'existe pas, on doit avoir une exception
        assertThrows(NoSuchElementException.class, () -> {
            commandeService.creerCommande("INEXISTANT");
        });
    }

    // ========== Ajout de lignes ==========

    @Test
    void ajouterUneLigneDeCommande() {
        // On crée d'abord une commande pour DSP05
        Commande cmd = commandeService.creerCommande("DSP05");

        // On ajoute le médicament 1 (Paracétamol 500mg, stock = 500) avec quantité 10
        Ligne ligne = commandeService.ajouterLigne(cmd.getNumero(), 1, 10);

        // La ligne doit être créée
        assertNotNull(ligne.getId(), "La ligne doit avoir un ID");
        assertEquals(10, ligne.getQuantite(), "La quantité doit être 10");
        assertEquals("Paracétamol 500mg", ligne.getMedicament().getNom());
    }

    @Test
    void ajouterDeuxFoisLeMemeMedicamentCumuleLesQuantites() {
        // On crée une commande
        Commande cmd = commandeService.creerCommande("DSP01");

        // On ajoute 5 unités du médicament 1
        commandeService.ajouterLigne(cmd.getNumero(), 1, 5);
        // On ajoute 3 unités du même médicament
        Ligne ligne = commandeService.ajouterLigne(cmd.getNumero(), 1, 3);

        // La quantité doit être cumulée (5 + 3 = 8)
        assertEquals(8, ligne.getQuantite(), "Les quantités doivent être cumulées");
    }

    @Test
    void impossibleDAjouterUnMedicamentIndisponible() {
        // On rend un médicament indisponible
        Medicament med = medicamentRepository.findById(1).orElseThrow();
        med.setIndisponible(true);
        medicamentRepository.saveAndFlush(med);

        // On crée une commande
        Commande cmd = commandeService.creerCommande("DSP01");

        // L'ajout doit échouer car le médicament est indisponible
        assertThrows(IllegalStateException.class, () -> {
            commandeService.ajouterLigne(cmd.getNumero(), 1, 10);
        });
    }

    @Test
    void impossibleDAjouterSiStockInsuffisant() {
        // Le médicament 1 a un stock de 500
        // On crée une commande et on essaie de commander 999 unités
        Commande cmd = commandeService.creerCommande("DSP01");

        assertThrows(IllegalStateException.class, () -> {
            commandeService.ajouterLigne(cmd.getNumero(), 1, 999);
        });
    }

    // ========== Expédition ==========

    @Test
    void expedierUneCommande() {
        // On crée une commande avec une ligne
        Commande cmd = commandeService.creerCommande("DSP01");
        commandeService.ajouterLigne(cmd.getNumero(), 1, 5);

        // On expédie la commande
        Commande cmdExpediee = commandeService.enregistreExpedition(cmd.getNumero());

        // La date d'envoi doit être renseignée maintenant
        assertNotNull(cmdExpediee.getEnvoyeele(), "La commande doit avoir une date d'envoi après expédition");
    }

    @Test
    void impossibleDExpedierDeuxFois() {
        // On crée et expédie une commande
        Commande cmd = commandeService.creerCommande("DSP01");
        commandeService.ajouterLigne(cmd.getNumero(), 1, 2);
        commandeService.enregistreExpedition(cmd.getNumero());

        // On essaie de l'expédier une deuxième fois
        assertThrows(IllegalStateException.class, () -> {
            commandeService.enregistreExpedition(cmd.getNumero());
        });
    }

    @Test
    void impossibleDAjouterUneLigneAUneCommandeExpediee() {
        // On crée, on ajoute une ligne, et on expédie
        Commande cmd = commandeService.creerCommande("DSP01");
        commandeService.ajouterLigne(cmd.getNumero(), 1, 2);
        commandeService.enregistreExpedition(cmd.getNumero());

        // On essaie d'ajouter une ligne après expédition
        assertThrows(IllegalStateException.class, () -> {
            commandeService.ajouterLigne(cmd.getNumero(), 2, 5);
        });
    }

    // ========== Commandes en cours ==========

    @Test
    void trouverLesCommandesEnCoursPourUnDispensaire() {
        // Dans data.sql, la commande 5 (DSP05) n'est pas envoyée
        List<Commande> enCours = commandeRepository.commandesEnCoursPour("DSP05");

        // Il doit y avoir au moins 1 commande en cours pour DSP05
        assertFalse(enCours.isEmpty(), "DSP05 doit avoir au moins une commande en cours");

        // Toutes les commandes en cours doivent avoir envoyeele == null
        for (Commande c : enCours) {
            assertNull(c.getEnvoyeele(), "Une commande en cours ne doit pas avoir de date d'envoi");
        }
    }
}
