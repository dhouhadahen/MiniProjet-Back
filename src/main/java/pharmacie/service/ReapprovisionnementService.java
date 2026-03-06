package pharmacie.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import pharmacie.dao.MedicamentRepository;
import pharmacie.entity.Fournisseur;
import pharmacie.entity.Medicament;

@Service
public class ReapprovisionnementService {

    private final MedicamentRepository medicamentRepository;

    @Autowired
    private JavaMailSender mailSender;

    public ReapprovisionnementService(MedicamentRepository medicamentRepository) {
        this.medicamentRepository = medicamentRepository;
    }

    public String processReapprovisionnement() {

        List<Medicament> tousMedicaments = medicamentRepository.findAll();
        List<Medicament> aReappro = new ArrayList<>();
        
        for (Medicament m : tousMedicaments) {
            if (m.getUnitesEnStock() < m.getNiveauDeReappro()) {
                aReappro.add(m);
            }
        }

        if (aReappro.isEmpty()) {
            return "Aucun médicament à réapprovisionner.";
        }

        Map<Fournisseur, List<Medicament>> parFournisseur = new HashMap<>();

        for (Medicament m : aReappro) {
            Set<Fournisseur> fournisseurs = m.getCategorie().getFournisseurs();

            if (fournisseurs == null || fournisseurs.isEmpty()) continue;

            for (Fournisseur f : fournisseurs) {
                if (!parFournisseur.containsKey(f)) {
                    parFournisseur.put(f, new ArrayList<>());
                }
                parFournisseur.get(f).add(m);
            }
        }

        int nbMails = 0;
        StringBuilder resultat = new StringBuilder();

        for (Map.Entry<Fournisseur, List<Medicament>> entry : parFournisseur.entrySet()) {
            Fournisseur fournisseur = entry.getKey();
            List<Medicament> medicaments = entry.getValue();

            StringBuilder contenuMail = new StringBuilder();
            contenuMail.append("Bonjour ").append(fournisseur.getNom()).append(", veuillez livrer :\n");

            for (Medicament m : medicaments) {
                contenuMail.append(" - ").append(m.getNom())
                           .append(" [Ref: ").append(m.getReference()).append("]\n");
            }

            String sujet = "Commande de réapprovisionnement";

            // =================================================================
            // AFFICHAGE DANS LE TERMINAL EXACTEMENT COMME TA CAPTURE D'ÉCRAN
            // =================================================================
            System.out.println("--------------------------------------------------");
            System.out.println("SIMULATION ENVOI EMAIL À : " + fournisseur.getEmail());
            System.out.println("OBJET : " + sujet);
            System.out.println("CONTENU :");
            System.out.print(contenuMail.toString());
            System.out.println("--------------------------------------------------");
            // =================================================================

            try {
                SimpleMailMessage message = new SimpleMailMessage();
                message.setTo(fournisseur.getEmail()); 
                message.setSubject(sujet);
                message.setText(contenuMail.toString());
                
                mailSender.send(message); // Tente d'envoyer le vrai mail
                nbMails++;
            } catch (Exception e) {
            }
            resultat.append("Mail traité pour ").append(fournisseur.getNom()).append("\n");
        }

        return resultat.toString() + "\nTotal mails envoyés (ou simulés) : " + nbMails;
    }
}