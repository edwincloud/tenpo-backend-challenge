package com.tenpo.challenge.infrastructure.persistence;

import com.tenpo.challenge.domain.model.CallRecord;
import com.tenpo.challenge.domain.port.CallHistoryRepository;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.relational.core.query.Query;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

/**
 * Implementa el puerto CallHistoryRepository con R2DBC. Uso R2dbcEntityTemplate (no el
 * CrudRepository solo) porque ReactiveCrudRepository no soporta Pageable/Page de por sí.
 */
@Repository
public class CallHistoryRepositoryAdapter implements CallHistoryRepository {

    private final CallHistoryR2dbcRepository crudRepository;
    private final R2dbcEntityTemplate template;

    public CallHistoryRepositoryAdapter(CallHistoryR2dbcRepository crudRepository, R2dbcEntityTemplate template) {
        this.crudRepository = crudRepository;
        this.template = template;
    }

    @Override
    public Mono<Void> save(CallRecord record) {
        return crudRepository.save(CallHistoryEntity.fromDomain(record)).then();
    }

    @Override
    public Mono<Page<CallRecord>> findAll(Pageable pageable) {
        Sort sort = pageable.getSortOr(Sort.by(Sort.Direction.DESC, "callTimestamp"));
        Query query = Query.empty().sort(sort).limit(pageable.getPageSize()).offset(pageable.getOffset());

        Mono<List<CallRecord>> content = template.select(CallHistoryEntity.class)
                .matching(query)
                .all()
                .map(CallHistoryEntity::toDomain)
                .collectList();

        Mono<Long> total = template.count(Query.empty(), CallHistoryEntity.class);

        return Mono.zip(content, total)
                .map(tuple -> new PageImpl<>(tuple.getT1(), pageable, tuple.getT2()));
    }
}
