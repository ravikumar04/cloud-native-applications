package com.jobinesh.example.grpc.hr.client;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.protobuf.Empty;
import com.google.protobuf.Int64Value;
import com.jobinesh.example.grpc.hr.data.DepartmentEntity;
import com.jobinesh.example.grpc.hr.service.Department;
import com.jobinesh.example.grpc.hr.service.DepartmentFilter;
import com.jobinesh.example.grpc.hr.service.DepartmentList;
import com.jobinesh.example.grpc.hr.service.HRServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;

import java.io.InputStream;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Grpc Client
 */
public class GrpcClient {

    private ManagedChannel channel;
    private HRServiceGrpc.HRServiceBlockingStub hrServiceBlockingStub;
    private HRServiceGrpc.HRServiceStub hrServiceAsyncStub;

    public GrpcClient() {
        initializeStub();
    }

    public static void main(String[] args) throws Exception {

        new GrpcClient().exercieAllAPIs();
    }

    private static void log(String message) {
        System.out.println(message);
    }

     private void exercieAllAPIs() throws InterruptedException {
        GrpcClient client = new GrpcClient();

        try {
            log("gRPC Client");
            Department dept = client.findDepartmentById(1000L);
            log(dept.toString());
            /*
             CountDownLatch finishLatch = new CountDownLatch(1);
            client.updateDepartmentsUsingStream(finishLatch);
            client.fetchAllDepartmentsUsingStream();
            client.updateDepartment(1000L);
            Department dept = client.findDepartmentById(1000L);
            DepartmentFilter filter = DepartmentFilter.newBuilder().build();
            List<Department> depts = client.findDepartmentByFilter(filter);
            client.deleteDepartment(1000L);
            dept = client.findDepartmentById(1000L);

            if (!finishLatch.await(10, TimeUnit.SECONDS)) {
                log("gRPC API call can not finish within 10 seconds");
            } */

        } catch (StatusRuntimeException e) {
            // Do not use Status.equals(...) - it's not well defined. Compare Code directly.
            if (e.getStatus().getCode() == Status.Code.DEADLINE_EXCEEDED) {
                log(e.getMessage());
            }
        } finally {

            client.shutdown();
        }
    }


    private void initializeStub() {
        channel = ManagedChannelBuilder.forAddress("localhost", 8080).usePlaintext().build();
        hrServiceBlockingStub = HRServiceGrpc.newBlockingStub(channel);
        hrServiceAsyncStub = HRServiceGrpc.newStub(channel);
    }

    public void shutdown() throws InterruptedException {
        channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
    }

    private Department findDepartmentById(Long id) {
        return hrServiceBlockingStub.findDepartmentById(Int64Value.of(id));
    }

    public Department updateDepartment(Long id) {
        Department dept = Department.newBuilder().setId(id)
                .setDepartmentName("Administration")
                .setLocation("Foster City")
                .build();
        hrServiceBlockingStub.updateDepartment(dept);
        return dept;
    }

    public List<Department> findDepartmentByFilter(DepartmentFilter filter) {

        DepartmentList departmentList = hrServiceBlockingStub.findDepartmentsByFilter(filter);
        return departmentList.getResultListList();
    }

    public void fetchAllDepartmentsUsingStream(final CountDownLatch finishLatch) throws InterruptedException {
        hrServiceAsyncStub.findAllDepartments(Empty.getDefaultInstance(), new StreamObserver<Department>() {
            public void onNext(Department department) {
                log("fetchAllDepartments:: Department ~ " + department);
                finishLatch.countDown();
            }

            public void onError(Throwable throwable) {
                finishLatch.countDown();
            }

            public void onCompleted() {
                finishLatch.countDown();
            }
        });

    }

    public void deleteDepartment(Long id) {
        hrServiceBlockingStub.deleteDepartment(Int64Value.of(id));
    }

    private List<DepartmentEntity> readHRDataFromFileStore() {
        List<DepartmentEntity> deptsList = null;
        try {
            ObjectMapper mapper = new ObjectMapper();
            InputStream jsonInput = this.getClass().getResourceAsStream("/test-data.json");
            deptsList = mapper.readValue(jsonInput,
                    new TypeReference<List<DepartmentEntity>>() {
                    });
        } catch (Exception ex) {
            ex.printStackTrace();
            deptsList = Collections.emptyList();
        }

        return deptsList;
    }

    private void updateDepartmentsUsingStream(final CountDownLatch finishLatch) throws InterruptedException {
        List<DepartmentEntity> deptsList = readHRDataFromFileStore();

        StreamObserver<Department> requestObserver = hrServiceAsyncStub.updateDepartmentsInBatch(new StreamObserver<Department>() {
            public void onNext(Department department) {
                log("updateDepartmentsInBatch:: Department ~ " + department);
            }

            public void onError(Throwable th) {
                th.printStackTrace();
            }

            public void onCompleted() {
                log("Completed!");
                finishLatch.countDown();
            }
        });

        try {
            for (DepartmentEntity dept : deptsList) {
                requestObserver.onNext(dept.toProto());
            }
        } catch (Exception ex) {
            requestObserver.onError(ex);
        }
        requestObserver.onCompleted();

    }
}
