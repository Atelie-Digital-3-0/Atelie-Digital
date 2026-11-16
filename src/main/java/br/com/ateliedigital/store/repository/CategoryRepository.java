package br.com.ateliedigital.store.repository;

import br.com.ateliedigital.store.model.CategoryModel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CategoryRepository extends JpaRepository<CategoryModel, UUID> {

    Optional<CategoryModel> findByName(String name);

    boolean existsByName(String name);

}
