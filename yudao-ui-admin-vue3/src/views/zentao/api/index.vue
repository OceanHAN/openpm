<template>
  <ContentWrap>
    <el-alert
      type="info"
      :closable="false"
      show-icon
      class="mb-15px"
      title="接口文档库（不是「对外 REST 接口管理」）"
      description="禅道 api 模块管理的是接口文档：库（zt_doclib 里 type='api'）→ 目录（zt_module type='api'）→ 接口（zt_api）→ 可复用结构（zt_apistruct）→ 发布版本（zt_api_lib_release）。三条照抄的规则：① 接口编辑时真有字段变更才 version+1，并把当前版本的快照原地重写（历史版本只追加）；② 发布版本存的是快照 JSON —— modules/apis/structs 冻成 snap，apis/structs 只存 id+version，内容仍在 zt_apispec / zt_apistruct_spec 里，读发布时按版本回查；③ title 在 (lib,module) 内唯一、path 在 (lib,module,method) 内唯一。"
    />
    <el-alert
      type="warning"
      :closable="false"
      show-icon
      class="mb-15px"
      title="导入 OpenAPI / Swagger 属付费扩展，本实现不做"
      description="禅道把 export / exportOpenApi / importOpenApi 三个 action 放在付费扩展 openapiimport 里：开源版走到 api/createLib 的 createMode=import 分支时直接返回 editionLimited（control.php:594），而 openapiimport 这个扩展在开源包里根本不存在（export/exportOpenApi/importOpenApi 连方法都没有）。所以本页只做手工维护接口与结构，不提供 Swagger 互导。"
    />

    <el-form class="-mb-15px" :inline="true" label-width="80px">
      <el-form-item label="接口库">
        <el-select
          v-model="queryParams.lib"
          class="!w-220px"
          clearable
          filterable
          placeholder="全部接口库"
          @change="onLibChange"
        >
          <el-option
            v-for="lib in libList"
            :key="lib.id"
            :label="`${lib.name}（${lib.apiCount ?? 0} 个接口 / ${lib.structCount ?? 0} 个结构）`"
            :value="lib.id!"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="目录">
        <el-tree-select
          v-model="queryParams.module"
          :data="moduleTree"
          :props="{ label: 'name', children: 'children' }"
          node-key="id"
          check-strictly
          clearable
          class="!w-180px"
          placeholder="全部目录（含子目录）"
          @change="handleQuery"
        />
      </el-form-item>
      <el-form-item label="接口名称">
        <el-input
          v-model="queryParams.title"
          placeholder="名称关键词"
          clearable
          class="!w-160px"
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="请求方式">
        <el-select v-model="queryParams.method" class="!w-120px" clearable placeholder="全部">
          <el-option v-for="m in METHODS" :key="m" :label="m" :value="m" />
        </el-select>
      </el-form-item>
      <el-form-item label="状态">
        <el-select v-model="queryParams.status" class="!w-120px" clearable placeholder="全部">
          <el-option v-for="s in STATUS_OPTIONS" :key="s.value" :label="s.label" :value="s.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="发布版本">
        <el-select
          v-model="queryParams.releaseID"
          class="!w-160px"
          clearable
          placeholder="看当前值"
          @change="handleQuery"
        >
          <el-option
            v-for="r in releaseList"
            :key="r.id"
            :label="`${r.version}（冻结 ${r.apiCount ?? 0} 个接口）`"
            :value="r.id!"
          />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button @click="handleQuery"><Icon icon="ep:search" class="mr-5px" /> 搜索</el-button>
        <el-button @click="resetQuery"><Icon icon="ep:refresh" class="mr-5px" /> 重置</el-button>
        <el-button type="primary" plain @click="openApiForm()" v-hasPermi="['zentao:api:create']">
          <Icon icon="ep:plus" class="mr-5px" /> 新建接口
        </el-button>
        <el-button plain @click="openStructForm" v-hasPermi="['zentao:api:create']">
          <Icon icon="ep:plus" class="mr-5px" /> 新建结构
        </el-button>
      </el-form-item>
    </el-form>
  </ContentWrap>

  <el-tabs v-model="tab" class="px-10px">
    <!-- ① 接口列表 -->
    <el-tab-pane label="接口列表" name="apis">
      <ContentWrap>
        <el-table v-loading="loading" :data="list" empty-text="没有符合条件的接口">
          <el-table-column label="接口名称" min-width="220" show-overflow-tooltip>
            <template #default="{ row }">
              <el-link type="primary" :underline="false" @click="openDetail(row)">{{ row.title }}</el-link>
              <el-tag v-if="queryParams.releaseID" type="warning" size="small" class="ml-5px">
                快照
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="请求" min-width="260" show-overflow-tooltip>
            <template #default="{ row }">
              <el-tag :type="methodTag(row.method)" size="small" class="mr-5px">{{ row.method }}</el-tag>
              <span class="font-mono">{{ row.path }}</span>
            </template>
          </el-table-column>
          <el-table-column label="目录" prop="moduleName" width="120" show-overflow-tooltip />
          <el-table-column label="状态" align="center" width="100">
            <template #default="{ row }">
              <el-tag :type="statusTag(row.status)" size="small">{{ row.statusName }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="版本" align="center" width="110">
            <template #default="{ row }">
              <el-tag size="small" type="info">v{{ row.version }}</el-tag>
              <span class="ml-5px text-12px text-gray-400">共 {{ row.versionCount ?? 0 }} 版</span>
            </template>
          </el-table-column>
          <el-table-column label="负责人" prop="owner" width="100" />
          <el-table-column label="修改时间" align="center" width="170">
            <template #default="{ row }">{{ formatDate(row.editedDate) }}</template>
          </el-table-column>
          <el-table-column label="操作" align="center" width="200" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="openDetail(row)">查看</el-button>
              <el-button link type="primary" @click="openApiForm(row)" v-hasPermi="['zentao:api:update']">
                编辑
              </el-button>
              <el-button link type="danger" @click="handleDeleteApi(row)" v-hasPermi="['zentao:api:delete']">
                删除
              </el-button>
            </template>
          </el-table-column>
        </el-table>
        <Pagination
          :total="total"
          v-model:page="queryParams.pageNo"
          v-model:limit="queryParams.pageSize"
          @pagination="getList"
        />
      </ContentWrap>
    </el-tab-pane>

    <!-- ② 数据结构 -->
    <el-tab-pane label="数据结构" name="structs">
      <ContentWrap>
        <el-alert
          type="info"
          :closable="false"
          show-icon
          class="mb-10px"
          title="结构是可复用字段树，接口的字段类型可以直接选结构"
          description="zt_apistruct.attribute 是可嵌套 JSON（field/paramsType/required/desc/children）。禅道的结构版本表 zt_apistruct_spec 只存 name、没有结构 id，读取时按 object.name = spec.name 关联 —— 这是禅道真实的设计缺陷（两个库里的同名结构会串版本），本实现如实保留。"
        />
        <el-table v-loading="structLoading" :data="structList" empty-text="这个库下还没有数据结构">
          <el-table-column label="结构名" min-width="160">
            <template #default="{ row }">
              <el-link type="primary" :underline="false" @click="openStructDetail(row)">
                {{ row.name }}
              </el-link>
            </template>
          </el-table-column>
          <el-table-column label="类型" align="center" width="100">
            <template #default="{ row }">{{ row.type }}</template>
          </el-table-column>
          <el-table-column label="版本" align="center" width="100">
            <template #default="{ row }">
              v{{ row.version }}
              <span class="ml-5px text-12px text-gray-400">共 {{ row.versionCount ?? 0 }} 版</span>
            </template>
          </el-table-column>
          <el-table-column label="字段数" align="center" width="90">
            <template #default="{ row }">{{ fieldRows(row.attribute).length }}</template>
          </el-table-column>
          <el-table-column label="说明" prop="desc" min-width="180" show-overflow-tooltip />
          <el-table-column label="创建人" width="140" show-overflow-tooltip>
            <template #default="{ row }">{{ row.addedName || row.addedBy }}</template>
          </el-table-column>
          <el-table-column label="创建时间" align="center" width="170">
            <template #default="{ row }">{{ formatDate(row.addedDate) }}</template>
          </el-table-column>
          <el-table-column label="操作" align="center" width="100" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="openStructDetail(row)">查看</el-button>
            </template>
          </el-table-column>
        </el-table>
        <Pagination
          :total="structTotal"
          v-model:page="structQuery.pageNo"
          v-model:limit="structQuery.pageSize"
          @pagination="getStructList"
        />
      </ContentWrap>
    </el-tab-pane>

    <!-- ③ 发布版本 -->
    <el-tab-pane label="发布版本" name="releases">
      <ContentWrap>
        <el-alert
          type="info"
          :closable="false"
          show-icon
          class="mb-10px"
          title="发布 = 把当前库的目录 + 接口 + 结构打成 snap JSON 冻结"
          description="apis / structs 只存 id 与当时的版本号，内容仍在 spec 表里：所以已发布的接口被删除后，历史版本照样能读。接口一旦被某个发布版本冻结，删除接口会被拒绝 —— 要么先删发布版本，要么就用快照看历史。"
        />
        <div class="mb-10px">
          <el-button type="primary" plain @click="openReleaseForm" v-hasPermi="['zentao:api:create']">
            <Icon icon="ep:upload" class="mr-5px" /> 发布当前库
          </el-button>
          <span class="ml-10px text-12px text-gray-400">
            发布前请先在上方选中「接口库」（快照打的是选中的那个库）
          </span>
        </div>
        <el-table v-loading="releaseLoading" :data="releaseList" empty-text="这个库还没有发布过版本">
          <el-table-column label="版本号" width="140">
            <template #default="{ row }">
              <el-tag type="success">{{ row.version }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="说明" prop="desc" min-width="180" show-overflow-tooltip />
          <el-table-column label="冻结目录" prop="moduleCount" align="center" width="100" />
          <el-table-column label="冻结接口" prop="apiCount" align="center" width="100" />
          <el-table-column label="冻结结构" prop="structCount" align="center" width="100" />
          <el-table-column label="冻结的接口清单" min-width="220" show-overflow-tooltip>
            <template #default="{ row }">{{ snapText(row.snapApis) }}</template>
          </el-table-column>
          <el-table-column label="发布人" prop="addedBy" width="100" />
          <el-table-column label="发布时间" align="center" width="170">
            <template #default="{ row }">{{ formatDate(row.addedDate) }}</template>
          </el-table-column>
          <el-table-column label="操作" align="center" width="180" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="browseRelease(row)">按此版本浏览</el-button>
              <el-button
                link
                type="danger"
                @click="handleDeleteRelease(row)"
                v-hasPermi="['zentao:api:delete']"
              >
                删除
              </el-button>
            </template>
          </el-table-column>
        </el-table>
      </ContentWrap>
    </el-tab-pane>
  </el-tabs>

  <!-- ==================== 接口详情抽屉 ==================== -->
  <el-drawer v-model="detailVisible" :title="`接口详情 #${detail?.id ?? ''}`" size="64%">
    <div v-loading="detailLoading">
      <el-alert
        v-if="detail && detail.viewingVersion !== 0"
        type="warning"
        :closable="false"
        show-icon
        class="mb-10px"
        :title="`正在看第 ${detail.viewingVersion} 版（当前版本 v${detail.version}）` +
          (detail.releaseVersion ? `，来自发布版本 ${detail.releaseVersion}` : '')"
      >
        <el-button link type="primary" @click="loadVersion(0)">回到当前版本</el-button>
      </el-alert>

      <el-descriptions :column="2" border class="mb-15px">
        <el-descriptions-item label="接口名称" :span="2">{{ detail?.title }}</el-descriptions-item>
        <el-descriptions-item label="请求方法">
          <el-tag :type="methodTag(detail?.method)" size="small">{{ detail?.method }}</el-tag>
          <span class="ml-5px font-mono">{{ detail?.path }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="协议 / 请求格式">
          {{ detail?.protocol || '-' }} / {{ detail?.requestType || '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="所属库">{{ detail?.libName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="目录">{{ detail?.moduleName || '（库根）' }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="statusTag(detail?.status)" size="small">{{ detail?.statusName }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="版本">
          v{{ detail?.version }}
          <span class="text-12px text-gray-400">（共 {{ detail?.versionCount ?? 0 }} 版）</span>
        </el-descriptions-item>
        <el-descriptions-item label="负责人">{{ detail?.owner || '-' }}</el-descriptions-item>
        <el-descriptions-item label="响应格式">
          {{ detail?.responseType || '（禅道表单里没有这一项，只读）' }}
        </el-descriptions-item>
        <el-descriptions-item label="创建人 / 时间">
          {{ detail?.addedBy }} / {{ formatDate(detail?.addedDate) }}
        </el-descriptions-item>
        <el-descriptions-item label="最后修改人 / 时间">
          {{ detail?.editedBy }} / {{ formatDate(detail?.editedDate) }}
        </el-descriptions-item>
        <el-descriptions-item label="接口说明" :span="2">{{ detail?.desc || '-' }}</el-descriptions-item>
      </el-descriptions>

      <el-card shadow="never" class="mb-15px">
        <template #header>
          <span class="font-bold">版本链</span>
          <span class="ml-10px text-12px text-gray-400">
            点版本号回看那一版；接口只在有变更时才 +1，快照是原地重写当前版本
          </span>
        </template>
        <el-tag
          v-for="v in detail?.versionList || []"
          :key="v.version"
          class="mr-5px mb-5px cursor-pointer"
          :type="v.version === detail?.viewingVersion ? 'primary' : 'info'"
          @click="loadVersion(v.version!)"
        >
          v{{ v.version }}（{{ v.addedBy }} {{ formatDate(v.addedDate) }}）
        </el-tag>
        <div v-if="!(detail?.versionList || []).length" class="text-gray-400">没有版本记录</div>
      </el-card>

      <el-card shadow="never" class="mb-15px">
        <template #header>
          <span class="font-bold">请求 / 响应字段</span>
          <span class="ml-10px text-12px text-gray-400">
            字段树的 paramsType 若是数字，说明类型指向本库的某个数据结构（下面是名字）
          </span>
        </template>
        <el-tabs v-model="scopeTab">
          <el-tab-pane
            v-for="scope in scopeTables"
            :key="scope.key"
            :label="`${scope.label}（${scope.rows.length}）`"
            :name="scope.key"
          >
            <el-table :data="scope.rows" size="small" empty-text="没有字段">
              <el-table-column label="字段" min-width="200">
                <template #default="{ row }">
                  <span :style="{ paddingLeft: row.level * 14 + 'px' }">
                    {{ row.field }}
                  </span>
                </template>
              </el-table-column>
              <el-table-column label="类型" width="200">
                <template #default="{ row }">
                  {{ row.paramsType }}
                  <el-tag v-if="structNameOf(row.paramsType)" size="small" type="success" class="ml-5px">
                    结构：{{ structNameOf(row.paramsType) }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column label="必填" align="center" width="70">
                <template #default="{ row }">{{ row.required ? '是' : '否' }}</template>
              </el-table-column>
              <el-table-column label="说明" prop="desc" min-width="200" show-overflow-tooltip />
            </el-table>
            <div v-if="scope.key === 'body'" class="mt-8px text-12px text-gray-400">
              请求体类型 paramsType：{{ parsedParams?.paramsType || '-' }}
            </div>
          </el-tab-pane>
        </el-tabs>
      </el-card>

      <el-row :gutter="15">
        <el-col :span="12">
          <el-card shadow="never">
            <template #header><span class="font-bold">请求示例</span></template>
            <pre class="api-pre">{{ detail?.paramsExample || '（空）' }}</pre>
          </el-card>
        </el-col>
        <el-col :span="12">
          <el-card shadow="never">
            <template #header><span class="font-bold">响应示例</span></template>
            <pre class="api-pre">{{ detail?.responseExample || '（空）' }}</pre>
          </el-card>
        </el-col>
      </el-row>
    </div>
  </el-drawer>

  <!-- ==================== 结构详情抽屉 ==================== -->
  <el-drawer v-model="structDetailVisible" :title="`数据结构 #${structDetail?.id ?? ''}`" size="52%">
    <div v-loading="structDetailLoading">
      <el-descriptions :column="2" border class="mb-15px">
        <el-descriptions-item label="结构名">{{ structDetail?.name }}</el-descriptions-item>
        <el-descriptions-item label="类型">{{ structDetail?.type }}</el-descriptions-item>
        <el-descriptions-item label="所属库">{{ structDetail?.libName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="版本">
          v{{ structDetail?.version }}
          <span class="text-12px text-gray-400">（共 {{ structDetail?.versionCount ?? 0 }} 版）</span>
        </el-descriptions-item>
        <el-descriptions-item label="创建人 / 时间" :span="2">
          {{ structDetail?.addedName || structDetail?.addedBy }} /
          {{ formatDate(structDetail?.addedDate) }}
        </el-descriptions-item>
        <el-descriptions-item label="说明" :span="2">{{ structDetail?.desc || '-' }}</el-descriptions-item>
      </el-descriptions>

      <el-card shadow="never" class="mb-15px">
        <template #header>
          <span class="font-bold">版本链</span>
          <span class="ml-10px text-12px text-gray-400">
            zt_apistruct_spec 只按 name 关联（禅道原样）
          </span>
        </template>
        <el-tag
          v-for="v in structDetail?.versionList || []"
          :key="v.version"
          class="mr-5px mb-5px cursor-pointer"
          :type="v.current ? 'primary' : 'info'"
          @click="loadStructVersion(v.version!)"
        >
          v{{ v.version }}（{{ v.addedBy }} {{ formatDate(v.addedDate) }}）
        </el-tag>
      </el-card>

      <el-card shadow="never">
        <template #header><span class="font-bold">字段树</span></template>
        <el-table :data="fieldRows(structDetail?.attribute)" size="small" empty-text="没有字段">
          <el-table-column label="字段" min-width="180">
            <template #default="{ row }">
              <span :style="{ paddingLeft: row.level * 14 + 'px' }">{{ row.field }}</span>
            </template>
          </el-table-column>
          <el-table-column label="类型" prop="paramsType" width="140" />
          <el-table-column label="必填" align="center" width="70">
            <template #default="{ row }">{{ row.required ? '是' : '否' }}</template>
          </el-table-column>
          <el-table-column label="说明" prop="desc" min-width="180" show-overflow-tooltip />
        </el-table>
        <div class="mt-10px text-12px text-gray-400">
          原始 JSON（本实现直接用 JSON 编辑字段树，禅道用的是可视化字段编辑器）
        </div>
        <pre class="api-pre">{{ prettyJson(structDetail?.attribute) }}</pre>
      </el-card>
    </div>
  </el-drawer>

  <!-- ==================== 接口表单 ==================== -->
  <el-dialog v-model="apiFormVisible" :title="apiFormTitle" width="720px" append-to-body>
    <el-form ref="apiFormRef" :model="apiForm" :rules="apiRules" label-width="110px">
      <el-form-item label="所属接口库" prop="lib">
        <el-select v-model="apiForm.lib" class="!w-260px" :disabled="!!apiForm.id" filterable>
          <el-option v-for="lib in libList" :key="lib.id" :label="lib.name" :value="lib.id!" />
        </el-select>
        <span class="ml-8px text-12px text-gray-400">库属于 zt_doclib，本页不改库（编辑时不可改）</span>
      </el-form-item>
      <el-form-item label="目录">
        <el-tree-select
          v-model="apiForm.module"
          :data="moduleTree"
          :props="{ label: 'name', children: 'children' }"
          node-key="id"
          check-strictly
          clearable
          class="!w-260px"
          placeholder="不选则挂在库根"
        />
      </el-form-item>
      <el-form-item label="接口名称" prop="title">
        <el-input v-model="apiForm.title" placeholder="(lib,module) 内唯一" />
      </el-form-item>
      <el-form-item label="请求路径" prop="path">
        <el-input v-model="apiForm.path" placeholder="(lib,module,method) 内唯一，如 /api.php/v1/user" />
      </el-form-item>
      <el-form-item label="协议 / 方式">
        <el-select v-model="apiForm.protocol" class="!w-140px">
          <el-option v-for="p in PROTOCOLS" :key="p" :label="p" :value="p" />
        </el-select>
        <el-select v-model="apiForm.method" class="ml-10px !w-140px">
          <el-option v-for="m in METHODS" :key="m" :label="m" :value="m" />
        </el-select>
      </el-form-item>
      <el-form-item label="请求格式">
        <el-select v-model="apiForm.requestType" class="!w-300px" clearable>
          <el-option v-for="r in REQUEST_TYPES" :key="r" :label="r" :value="r" />
        </el-select>
      </el-form-item>
      <el-form-item label="开发状态">
        <el-radio-group v-model="apiForm.status">
          <el-radio v-for="s in STATUS_OPTIONS" :key="s.value" :value="s.value">{{ s.label }}</el-radio>
        </el-radio-group>
      </el-form-item>
      <el-form-item label="负责人">
        <el-input v-model="apiForm.owner" class="!w-200px" placeholder="账号，如 admin" />
      </el-form-item>
      <el-form-item label="接口说明">
        <el-input v-model="apiForm.desc" type="textarea" :rows="2" />
      </el-form-item>
      <el-form-item label="请求参数树">
        <el-input v-model="apiForm.params" type="textarea" :rows="4" :placeholder="PLACEHOLDER_PARAMS" />
      </el-form-item>
      <el-form-item label="请求示例">
        <el-input v-model="apiForm.paramsExample" type="textarea" :rows="2" />
      </el-form-item>
      <el-form-item label="响应字段树">
        <el-input v-model="apiForm.response" type="textarea" :rows="4" :placeholder="PLACEHOLDER_RESPONSE" />
      </el-form-item>
      <el-form-item label="响应示例">
        <el-input v-model="apiForm.responseExample" type="textarea" :rows="3" />
      </el-form-item>
      <el-alert
        type="info"
        :closable="false"
        show-icon
        title="编辑的两个照抄行为"
        description="① 传进来的 null 视为「这一项不改」，空字符串才是「清空」；② 只有真的改动了字段，版本号才 +1，否则只原地重写当前版本的快照。responseType 与 commonParams 不在禅道的编辑表单里，本表单也没有它们。"
      />
    </el-form>
    <template #footer>
      <el-button @click="apiFormVisible = false">取 消</el-button>
      <el-button type="primary" :loading="saving" @click="submitApiForm">确 定</el-button>
    </template>
  </el-dialog>

  <!-- ==================== 结构表单 ==================== -->
  <el-dialog v-model="structFormVisible" title="新建数据结构" width="640px" append-to-body>
    <el-form ref="structFormRef" :model="structForm" :rules="structRules" label-width="110px">
      <el-form-item label="所属接口库" prop="lib">
        <el-select v-model="structForm.lib" class="!w-260px" filterable>
          <el-option v-for="lib in libList" :key="lib.id" :label="lib.name" :value="lib.id!" />
        </el-select>
      </el-form-item>
      <el-form-item label="结构名" prop="name">
        <el-input v-model="structForm.name" placeholder="如 user；版本表按这个名字关联，注意跨库同名会串版本" />
      </el-form-item>
      <el-form-item label="结构类型">
        <el-select v-model="structForm.type" class="!w-200px">
          <el-option v-for="t in STRUCT_TYPES" :key="t" :label="t" :value="t" />
        </el-select>
      </el-form-item>
      <el-form-item label="结构说明">
        <el-input v-model="structForm.desc" type="textarea" :rows="2" />
      </el-form-item>
      <el-form-item label="字段树 JSON">
        <el-input
          v-model="structForm.attribute"
          type="textarea"
          :rows="5"
          :placeholder="PLACEHOLDER_STRUCT"
        />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="structFormVisible = false">取 消</el-button>
      <el-button type="primary" :loading="saving" @click="submitStructForm">确 定</el-button>
    </template>
  </el-dialog>

  <!-- ==================== 发布表单 ==================== -->
  <el-dialog v-model="releaseFormVisible" title="发布接口库（打快照）" width="560px" append-to-body>
    <el-form ref="releaseFormRef" :model="releaseForm" :rules="releaseRules" label-width="90px">
      <el-form-item label="接口库">
        <el-select v-model="releaseForm.lib" class="!w-260px" filterable>
          <el-option v-for="lib in libList" :key="lib.id" :label="lib.name" :value="lib.id!" />
        </el-select>
      </el-form-item>
      <el-form-item label="版本号" prop="version">
        <el-input v-model="releaseForm.version" class="!w-200px" placeholder="字符串，同库内唯一，如 v1.1" />
      </el-form-item>
      <el-form-item label="版本说明">
        <el-input v-model="releaseForm.desc" />
      </el-form-item>
      <el-alert
        type="warning"
        :closable="false"
        show-icon
        title="发布是冻结，不是引用"
        description="会把当前库的目录、接口（id+当前版本号）、结构（id+当前版本号）写进 snap JSON。冻结之后这些接口就不能再删除了（要删先删发布版本）—— 这是本实现加的保护，禅道本身没有这道检查。"
      />
    </el-form>
    <template #footer>
      <el-button @click="releaseFormVisible = false">取 消</el-button>
      <el-button type="primary" :loading="saving" @click="submitReleaseForm">确 定</el-button>
    </template>
  </el-dialog>
</template>

<script lang="ts" setup>
import * as ApiApi from '@/api/zentao/api'
import { getModuleTree } from '@/api/zentao/module'
import { formatDate } from '@/utils/formatTime'

defineOptions({ name: 'ZentaoApi' })

const message = useMessage()

const METHODS = ['GET', 'POST', 'PUT', 'DELETE', 'PATCH', 'OPTIONS', 'HEAD']
const PROTOCOLS = ['HTTP', 'HTTPS', 'WS', 'WSS']
const REQUEST_TYPES = [
  'application/json',
  'application/x-www-form-urlencoded',
  'multipart/form-data'
]
// 禅道 $lang->struct->typeOptions（结构类型）与 $lang->api->statusOptions（状态）
const STRUCT_TYPES = ['formData', 'json', 'array', 'object']
const STATUS_OPTIONS = [
  { value: 'done', label: '开发完成' },
  { value: 'doing', label: '开发中' },
  { value: 'hidden', label: '不显示' }
]

// 表单占位文案（形状照抄禅道 params 列的四个键与字段树节点）
const PLACEHOLDER_PARAMS = '{"header":[],"params":[],"paramsType":"formData","query":[]}'
const PLACEHOLDER_RESPONSE = '[{"field":"id","paramsType":"int","desc":"编号","children":[]}]'
const PLACEHOLDER_STRUCT =
  '[{"field":"id","paramsType":"int","required":true,"desc":"编号","children":[]}]'

const tab = ref('apis')
const loading = ref(false)
const total = ref(0)
const list = ref<ApiApi.ApiVO[]>([])
const libList = ref<ApiApi.ApiLibVO[]>([])
const moduleTree = ref<any[]>([])
const releaseList = ref<ApiApi.ApiReleaseVO[]>([])
const releaseLoading = ref(false)

const queryParams = reactive({
  pageNo: 1,
  pageSize: 20,
  lib: undefined as number | undefined,
  module: undefined as number | undefined,
  title: '',
  method: undefined as string | undefined,
  status: undefined as string | undefined,
  releaseID: undefined as number | undefined
})

// ==================== 结构列表 ====================
const structLoading = ref(false)
const structTotal = ref(0)
const structList = ref<ApiApi.ApiStructVO[]>([])
const structQuery = reactive({ pageNo: 1, pageSize: 20, lib: undefined as number | undefined })

// ==================== 详情抽屉 ====================
const detailVisible = ref(false)
const detailLoading = ref(false)
const detail = ref<ApiApi.ApiVO>()
const scopeTab = ref('header')
const structNameMap = ref<Record<string, string>>({})

const parsedParams = computed(() => {
  const parsed = safeParse(detail.value?.params)
  return parsed && typeof parsed === 'object' ? (parsed as any) : undefined
})

const scopeTables = computed(() => {
  const params = parsedParams.value || {}
  return [
    { key: 'header', label: '请求头', rows: fieldRows(params.header) },
    { key: 'query', label: '请求参数(query)', rows: fieldRows(params.query) },
    { key: 'body', label: '请求体', rows: fieldRows(params.params) },
    { key: 'response', label: '响应', rows: fieldRows(detail.value?.response) }
  ]
})

// ==================== 表单 ====================
const saving = ref(false)
const apiFormVisible = ref(false)
const apiFormTitle = ref('')
const apiFormRef = ref()
const apiForm = ref<ApiApi.ApiVO>({})
const apiRules = {
  lib: [{ required: true, message: '请选择接口库', trigger: 'change' }],
  title: [{ required: true, message: '接口名称不能为空', trigger: 'blur' }],
  path: [{ required: true, message: '请求路径不能为空', trigger: 'blur' }]
}

const structFormVisible = ref(false)
const structFormRef = ref()
const structForm = ref<ApiApi.ApiStructVO>({})
const structRules = {
  lib: [{ required: true, message: '请选择接口库', trigger: 'change' }],
  name: [{ required: true, message: '结构名不能为空', trigger: 'blur' }]
}

const releaseFormVisible = ref(false)
const releaseFormRef = ref()
const releaseForm = reactive({ lib: undefined as number | undefined, version: '', desc: '' })
const releaseRules = {
  lib: [{ required: true, message: '请选择接口库', trigger: 'change' }],
  version: [{ required: true, message: '版本号不能为空', trigger: 'blur' }]
}

// ==================== 工具 ====================

/** JSON 安全解析：解析不了就返回 null（页面绝不因为一条脏 JSON 白屏） */
const safeParse = (text?: string) => {
  if (!text) return null
  try {
    return JSON.parse(text)
  } catch {
    return null
  }
}

/** 把嵌套字段树摊平成表格行，用 level 控制缩进 */
const fieldRows = (raw?: any, level = 0): any[] => {
  const nodes = typeof raw === 'string' ? safeParse(raw) : raw
  if (!Array.isArray(nodes)) return []
  const rows: any[] = []
  for (const node of nodes) {
    if (!node || typeof node !== 'object') continue
    rows.push({ ...node, level })
    if (Array.isArray(node.children) && node.children.length) {
      rows.push(...fieldRows(node.children, level + 1))
    }
  }
  return rows
}

const prettyJson = (text?: string) => {
  const parsed = safeParse(text)
  return parsed === null ? text || '（空）' : JSON.stringify(parsed, null, 2)
}

const methodTag = (method?: string) => {
  const map: Record<string, any> = { GET: 'success', POST: 'primary', PUT: 'warning', DELETE: 'danger' }
  return map[method || ''] || 'info'
}

const statusTag = (status?: string) => {
  const map: Record<string, any> = { done: 'success', doing: 'warning', hidden: 'info' }
  return map[status || ''] || 'info'
}

/** paramsType 是数字时指向结构编号，这里翻成结构名 */
const structNameOf = (paramsType?: string) => {
  if (!paramsType) return ''
  return structNameMap.value[paramsType] || ''
}

const snapText = (items?: ApiApi.ApiSnapItemVO[]) =>
  (items || []).map((i) => `#${i.id}@v${i.version}`).join('、') || '（空）'

// ==================== 加载 ====================

const loadLibs = async () => {
  libList.value = await ApiApi.getApiLibList()
}

const loadModuleTree = async () => {
  // 目录树复用 module 模块：root=接口库、type='api'（禅道 zt_module 就是这么定位一棵树的）
  moduleTree.value = queryParams.lib
    ? await getModuleTree({ root: queryParams.lib, type: 'api' })
    : []
}

const loadReleases = async () => {
  releaseLoading.value = true
  try {
    releaseList.value = await ApiApi.getApiReleaseList(queryParams.lib)
  } finally {
    releaseLoading.value = false
  }
}

/** 结构名映射：给字段树的「类型」列把结构编号翻成名字（也证明接口确实挂了结构） */
const loadStructNames = async () => {
  if (!queryParams.lib) {
    structNameMap.value = {}
    return
  }
  const page = await ApiApi.getApiStructPage({ lib: queryParams.lib, pageNo: 1, pageSize: 100 })
  const map: Record<string, string> = {}
  for (const struct of page.list || []) {
    map[String(struct.id)] = struct.name!
  }
  structNameMap.value = map
}

const getList = async () => {
  loading.value = true
  try {
    const data = await ApiApi.getApiPage(queryParams)
    list.value = data.list ?? []
    total.value = data.total ?? 0
  } finally {
    loading.value = false
  }
}

const getStructList = async () => {
  structLoading.value = true
  try {
    const data = await ApiApi.getApiStructPage({ ...structQuery, lib: queryParams.lib })
    structList.value = data.list ?? []
    structTotal.value = data.total ?? 0
  } finally {
    structLoading.value = false
  }
}

const handleQuery = async () => {
  queryParams.pageNo = 1
  await getList()
}

const resetQuery = async () => {
  queryParams.lib = undefined
  queryParams.module = undefined
  queryParams.title = ''
  queryParams.method = undefined
  queryParams.status = undefined
  queryParams.releaseID = undefined
  moduleTree.value = []
  await handleQuery()
}

/**
 * 换库：目录树、发布版本、结构列表都跟着换。
 * 发布版本筛选必须清掉 —— 一个发布只属于一个库，留着旧 releaseID 会查出空页。
 */
const onLibChange = async () => {
  queryParams.module = undefined
  queryParams.releaseID = undefined
  await Promise.all([loadModuleTree(), loadReleases(), loadStructNames(), getStructList()])
  await handleQuery()
}

// ==================== 详情 ====================

const openDetail = async (row: ApiApi.ApiVO) => {
  detailVisible.value = true
  await loadVersion(0, row.id!)
}

const loadVersion = async (version = 0, id?: number) => {
  const apiId = id ?? detail.value?.id
  if (!apiId) return
  detailLoading.value = true
  try {
    // version=0 表示看当前值，这时才让发布版本生效（releaseID 优先）；
    // 显式点了版本号就以点的那一版为准，否则「点 v1 却看到冻结版」会很困惑
    const releaseID = version ? undefined : queryParams.releaseID
    detail.value = await ApiApi.getApi(apiId, version || undefined, releaseID)
    scopeTab.value = 'header'
  } finally {
    detailLoading.value = false
  }
}

const browseRelease = async (row: ApiApi.ApiReleaseVO) => {
  queryParams.releaseID = row.id
  tab.value = 'apis'
  await handleQuery()
  message.info(`已切到发布版本 ${row.version} 的快照视图（接口列表顶部会看到「快照」标记）`)
}

// ==================== 接口表单 ====================

const openApiForm = (row?: ApiApi.ApiVO) => {
  apiFormTitle.value = row ? `编辑接口：${row.title}` : '新建接口'
  apiForm.value = row
    ? {
        ...row,
        // editedDate 是乐观锁：列表里拿到的是**数字时间戳**（yudao 全局把 LocalDateTime 序列化成毫秒），
        // 而后端这个字段的反序列化器只认 "yyyy-MM-dd HH:mm:ss"（坑位 #21/#30），
        // 所以提交前必须转成字符串，否则后端 400。
        editedDate: row.editedDate ? formatDate(row.editedDate) : undefined
      }
    : {
        lib: queryParams.lib,
        module: queryParams.module,
        protocol: 'HTTP',
        method: 'GET',
        status: 'done',
        owner: 'admin',
        params: '{"header":[],"params":[],"paramsType":"formData","query":[]}'
      }
  apiFormVisible.value = true
}

const submitApiForm = async () => {
  // Element Plus 的 validate() 失败时是 reject，不 catch 就会冒一条 unhandledrejection（坑位 #53）
  try {
    await apiFormRef.value.validate()
  } catch {
    return
  }
  saving.value = true
  try {
    if (apiForm.value.id) {
      await ApiApi.updateApi(apiForm.value)
      message.success('已保存；有字段变更时版本号 +1')
    } else {
      await ApiApi.createApi(apiForm.value)
      message.success('已创建，并写入 v1 快照')
    }
    apiFormVisible.value = false
    await Promise.all([getList(), loadLibs(), loadStructNames(), loadReleases()])
  } finally {
    saving.value = false
  }
}

const handleDeleteApi = async (row: ApiApi.ApiVO) => {
  await message.delConfirm(
    `确认删除「${row.title}」？被发布版本冻结、或被数据结构引用时会被拒绝`
  )
  try {
    await ApiApi.deleteApi(row.id!)
  } catch {
    // 被引用时的业务提示由 axios 拦截器弹出，这里只要别把异常再抛出去
    return
  }
  message.success('已删除（只软删接口头部，历史版本仍在 zt_apispec 里）')
  await Promise.all([getList(), loadLibs()])
}

// ==================== 结构 ====================

const structDetailVisible = ref(false)
const structDetailLoading = ref(false)
const structDetail = ref<ApiApi.ApiStructVO>()

const openStructDetail = async (row: ApiApi.ApiStructVO) => {
  structDetailVisible.value = true
  await loadStructVersion(0, row.id!)
}

const loadStructVersion = async (version = 0, id?: number) => {
  const structId = id ?? structDetail.value?.id
  if (!structId) return
  structDetailLoading.value = true
  try {
    structDetail.value = await ApiApi.getApiStruct(structId, version || undefined)
  } finally {
    structDetailLoading.value = false
  }
}

const openStructForm = () => {
  structForm.value = {
    lib: queryParams.lib,
    type: 'json',
    attribute: '[{"field":"id","paramsType":"int","required":true,"desc":"编号","children":[]}]'
  }
  structFormVisible.value = true
}

const submitStructForm = async () => {
  try {
    await structFormRef.value.validate()
  } catch {
    return
  }
  saving.value = true
  try {
    await ApiApi.createApiStruct(structForm.value)
    message.success('已创建结构，并写入 v1 版本')
    structFormVisible.value = false
    await Promise.all([getStructList(), loadLibs(), loadStructNames()])
  } finally {
    saving.value = false
  }
}

// ==================== 发布版本 ====================

const openReleaseForm = () => {
  releaseForm.lib = queryParams.lib
  releaseForm.version = ''
  releaseForm.desc = ''
  releaseFormVisible.value = true
}

const submitReleaseForm = async () => {
  try {
    await releaseFormRef.value.validate()
  } catch {
    return
  }
  saving.value = true
  try {
    await ApiApi.createApiRelease({
      lib: releaseForm.lib!,
      version: releaseForm.version,
      desc: releaseForm.desc
    })
    message.success('已发布：目录/接口/结构已冻结成 snap 快照')
    releaseFormVisible.value = false
    await loadReleases()
  } finally {
    saving.value = false
  }
}

const handleDeleteRelease = async (row: ApiApi.ApiReleaseVO) => {
  await message.delConfirm(`确认删除发布版本 ${row.version}？删除后它冻结的接口才能被删除`)
  try {
    await ApiApi.deleteApiRelease(row.id!)
  } catch {
    return
  }
  message.success('已删除发布版本')
  if (queryParams.releaseID === row.id) {
    queryParams.releaseID = undefined
    await getList()
  }
  await loadReleases()
}

onMounted(async () => {
  await loadLibs()
  // 只有一个接口库时直接选中（禅道也是默认打开第一个库），这样目录树/发布版本一进来就有内容
  if (!queryParams.lib && libList.value.length === 1) {
    queryParams.lib = libList.value[0].id
  }
  if (queryParams.lib) {
    await Promise.all([loadModuleTree(), loadStructNames()])
  }
  await Promise.all([getList(), loadReleases(), getStructList()])
})
</script>

<style scoped>
.api-pre {
  margin: 0;
  padding: 8px;
  max-height: 260px;
  overflow: auto;
  background: var(--el-fill-color-light);
  border-radius: 4px;
  font-size: 12px;
  line-height: 1.5;
  white-space: pre-wrap;
  word-break: break-all;
}

.cursor-pointer {
  cursor: pointer;
}
</style>
