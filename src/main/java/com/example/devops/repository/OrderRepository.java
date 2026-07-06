package com.example.devops.repository;

import com.example.devops.model.DailySales;
import com.example.devops.model.Order;
import com.example.devops.model.StatusCount;
import com.example.devops.model.TopProduct;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {

    // 1. 일별 주문수 및 매출 총합 통계 (성능 최적화 대상 - Group By 및 Date Function 사용)
    @Query(value = "SELECT TO_CHAR(order_date, 'YYYY-MM-DD') as orderDate, " +
                   "COUNT(*) as totalOrders, " +
                   "SUM(price * quantity) as totalSales " +
                   "FROM orders " +
                   "GROUP BY TO_CHAR(order_date, 'YYYY-MM-DD') " +
                   "ORDER BY orderDate DESC LIMIT 30", nativeQuery = true)
    List<DailySales> getDailySales();

    // 2. 가장 많이 판매된 상위 5개 상품 및 매출 (성능 최적화 대상 - Group By 및 Order By, Limit 사용)
    @Query(value = "SELECT product_name as productName, " +
                   "SUM(quantity) as totalQuantity, " +
                   "SUM(price * quantity) as totalRevenue " +
                   "FROM orders " +
                   "GROUP BY product_name " +
                   "ORDER BY totalRevenue DESC LIMIT 5", nativeQuery = true)
    List<TopProduct> getTopProducts();

    // 3. 주문 상태별 분포 통계
    @Query(value = "SELECT status as status, COUNT(*) as count " +
                   "FROM orders " +
                   "GROUP BY status", nativeQuery = true)
    List<StatusCount> getStatusDistribution();
}
