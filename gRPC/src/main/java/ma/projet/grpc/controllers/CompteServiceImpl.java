package ma.projet.grpc.controllers;

import io.grpc.stub.StreamObserver;
import ma.projet.grpc.repositories.CompteRepository;
import ma.projet.grpc.stubs.CompteRequest;
import ma.projet.grpc.stubs.CompteServiceGrpc;
import ma.projet.grpc.stubs.GetAllComptesRequest;
import ma.projet.grpc.stubs.GetAllComptesResponse;
import ma.projet.grpc.stubs.GetCompteByIdRequest;
import ma.projet.grpc.stubs.GetCompteByIdResponse;
import ma.projet.grpc.stubs.GetTotalSoldeRequest;
import ma.projet.grpc.stubs.GetTotalSoldeResponse;
import ma.projet.grpc.stubs.SaveCompteRequest;
import ma.projet.grpc.stubs.SaveCompteResponse;
import ma.projet.grpc.stubs.SoldeStats;
import ma.projet.grpc.stubs.TypeCompte;

import net.devh.boot.grpc.server.service.GrpcService;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@GrpcService
public class CompteServiceImpl extends CompteServiceGrpc.CompteServiceImplBase {

    @Autowired
    private CompteRepository compteRepository;

    /**
     * ✅ Conversion Entity JPA -> Stub gRPC
     * ⚠️ Ici on utilise le nom complet ma.projet.grpc.entities.Compte
     * pour éviter le conflit avec ma.projet.grpc.stubs.Compte
     */
    private ma.projet.grpc.stubs.Compte toGrpc(ma.projet.grpc.entities.Compte e) {

        // Si ton entity type est String: "COURANT"/"EPARGNE"
        TypeCompte typeGrpc = "COURANT".equalsIgnoreCase(String.valueOf(e.getType()))
                ? TypeCompte.COURANT
                : TypeCompte.EPARGNE;

        return ma.projet.grpc.stubs.Compte.newBuilder()
                .setId(String.valueOf(e.getId()))
                .setSolde((float) e.getSolde()) // si ton entity est double => cast en float (proto = float)
                .setDateCreation(String.valueOf(e.getDateCreation()))
                .setType(typeGrpc)
                .build();
    }

    /**
     * ✅ Conversion Stub gRPC -> Entity JPA
     */
    private ma.projet.grpc.entities.Compte toEntity(CompteRequest req) {
        ma.projet.grpc.entities.Compte e = new ma.projet.grpc.entities.Compte();

        // solde
        e.setSolde(req.getSolde());

        // dateCreation (proto string) => on la met comme String
        e.setDateCreation(req.getDateCreation());

        // type enum -> String (COURANT/EPARGNE)
        e.setType(req.getType().name());

        return e;
    }

    @Override
    public void allComptes(GetAllComptesRequest request,
                           StreamObserver<GetAllComptesResponse> responseObserver) {

        List<ma.projet.grpc.entities.Compte> entities = compteRepository.findAll();

        List<ma.projet.grpc.stubs.Compte> grpcComptes = entities.stream()
                .map(this::toGrpc)
                .collect(Collectors.toList());

        GetAllComptesResponse response = GetAllComptesResponse.newBuilder()
                .addAllComptes(grpcComptes)
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    @Override
    public void compteById(GetCompteByIdRequest request,
                           StreamObserver<GetCompteByIdResponse> responseObserver) {

        String id = request.getId();  // String

        Optional<ma.projet.grpc.entities.Compte> opt = compteRepository.findById(id);

        GetCompteByIdResponse response = GetCompteByIdResponse.newBuilder()
                .setCompte(opt.map(this::toGrpc)
                        .orElse(ma.projet.grpc.stubs.Compte.newBuilder().build()))
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    @Override
    public void totalSolde(GetTotalSoldeRequest request,
                           StreamObserver<GetTotalSoldeResponse> responseObserver) {

        List<ma.projet.grpc.entities.Compte> entities = compteRepository.findAll();

        int count = entities.size();
        float sum = 0f;

        for (ma.projet.grpc.entities.Compte c : entities) {
            sum += (float) c.getSolde();
        }

        float avg = (count == 0) ? 0f : (sum / count);

        SoldeStats stats = SoldeStats.newBuilder()
                .setCount(count)
                .setSum(sum)
                .setAverage(avg)
                .build();

        GetTotalSoldeResponse response = GetTotalSoldeResponse.newBuilder()
                .setStats(stats)
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    @Override
    public void saveCompte(SaveCompteRequest request,
                           StreamObserver<SaveCompteResponse> responseObserver) {

        // ✅ IMPORTANT : req doit être déclaré sinon "cannot find symbol"
        CompteRequest req = request.getCompte();

        // Convertir vers entity + save
        ma.projet.grpc.entities.Compte entity = toEntity(req);
        ma.projet.grpc.entities.Compte savedEntity = compteRepository.save(entity);

        // Convertir vers stub pour répondre
        ma.projet.grpc.stubs.Compte savedGrpc = toGrpc(savedEntity);

        SaveCompteResponse response = SaveCompteResponse.newBuilder()
                .setCompte(savedGrpc)
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }
}
