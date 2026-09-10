package com.outpost.common.iso.repository.sanity;

import com.outpost.common.iso.repository.CurrencyRecord;
import com.outpost.framework.persistence.RegisteredMapper;
import java.util.List;
import org.apache.ibatis.annotations.Select;

/** Read-only MyBatis mapper for currencies. */
@RegisteredMapper
public interface CurrencyStaticDataMapper {
  /** Returns all currency records. */
  @Select("SELECT currency_id, currency_code, exponent FROM currency ORDER BY currency_id")
  List<CurrencyRecord> findAll();
}
