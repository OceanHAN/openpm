<template>
  <ContentWrap>
    <el-alert
      type="info"
      :closable="false"
      show-icon
      class="mb-15px"
      title="组织与权限：不迁移，映射到 yudao 的 RBAC"
      description="zt_user / zt_dept / zt_group / zt_grouppriv 不做表迁移，而是映射到 system_users / system_dept / system_role / system_role_menu。原因是 yudao 已覆盖「用户-角色-菜单-权限 + 数据范围」，再搬一套权限包会长期双写。本页把 yudao 的权限翻译回禅道的 (模块, 方法) 视角，供迁移评审逐条对照。"
    />
    <el-alert
      type="warning"
      :closable="false"
      show-icon
      class="mb-15px"
      title="一个必须知道的差异：超级管理员是「硬编码放行」"
      description="yudao 的 super_admin 不走权限表（PermissionServiceImpl 直接返回 true），所以按表查它的权限是 0 条。本页对超管做了等价展开（= 系统里全部禅道权限），否则评审时会误判超管什么都不能做。"
    />
  </ContentWrap>

  <ContentWrap>
    <el-tabs v-model="activeTab">
      <!-- ==================== 用户 ==================== -->
      <el-tab-pane :label="`用户（${users.length}）`" name="user">
        <el-form :inline="true" class="-mb-15px">
          <el-form-item label="账号/姓名">
            <el-input v-model="userQuery.keyword" placeholder="模糊匹配" clearable class="!w-180px" @keyup.enter="loadUsers" />
          </el-form-item>
          <el-form-item label="状态">
            <el-select v-model="userQuery.status" placeholder="全部" clearable class="!w-120px">
              <el-option label="开启" :value="0" />
              <el-option label="停用" :value="1" />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button @click="loadUsers"><Icon icon="ep:search" class="mr-5px" /> 查询</el-button>
            <el-button @click="resetUserQuery"><Icon icon="ep:refresh" class="mr-5px" /> 重置</el-button>
          </el-form-item>
        </el-form>

        <el-table v-loading="loading" :data="users" class="mt-15px">
          <el-table-column label="账号" prop="account" width="120" />
          <el-table-column label="姓名" prop="realname" width="140" show-overflow-tooltip />
          <el-table-column label="部门" prop="deptName" width="140" show-overflow-tooltip />
          <el-table-column label="权限包（角色）" prop="roleNames" min-width="180" show-overflow-tooltip />
          <el-table-column label="可访问模块" min-width="220">
            <template #default="{ row }">
              <el-tag v-for="m in (row.modules || []).slice(0, 6)" :key="m" size="small" class="mr-4px">{{ m }}</el-tag>
              <el-tooltip v-if="(row.modules || []).length > 6" :content="(row.modules || []).join(', ')">
                <el-tag size="small" type="info">+{{ row.modules.length - 6 }}</el-tag>
              </el-tooltip>
              <span v-if="(row.modules || []).length === 0" class="text-gray-400">无禅道权限</span>
            </template>
          </el-table-column>
          <el-table-column label="权限条数" align="center" width="100">
            <template #default="{ row }">
              <el-button link type="primary" @click="openUserPermissions(row)">{{ row.permissionCount }}</el-button>
            </template>
          </el-table-column>
          <el-table-column label="状态" align="center" width="90">
            <template #default="{ row }">
              <el-tag :type="row.status === 0 ? 'success' : 'info'">{{ row.status === 0 ? '开启' : '停用' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="邮箱" prop="email" min-width="170" show-overflow-tooltip />
          <el-table-column label="手机" prop="mobile" width="130" />
          <el-table-column label="最后登录" align="center" width="170" prop="loginDate" :formatter="dateFormatter" />
        </el-table>
      </el-tab-pane>

      <!-- ==================== 权限包 ==================== -->
      <el-tab-pane :label="`权限包 / 角色（${roles.length}）`" name="role">
        <el-table v-loading="loading" :data="roles">
          <el-table-column label="编号" align="center" prop="id" width="70" />
          <el-table-column label="权限包" prop="name" min-width="150" show-overflow-tooltip />
          <el-table-column label="编码" prop="code" width="140" />
          <el-table-column label="数据范围" align="center" prop="dataScopeName" width="140" />
          <el-table-column label="成员数" align="center" prop="userCount" width="90" />
          <el-table-column label="禅道权限条数" align="center" width="120">
            <template #default="{ row }">
              <el-tag :type="row.code === 'super_admin' ? 'danger' : 'info'">
                {{ row.zentaoPermissionCount }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="覆盖模块" min-width="260">
            <template #default="{ row }">
              <el-tag v-for="p in (row.permissions || [])" :key="p.module" size="small" class="mr-4px mb-4px">
                {{ p.moduleName }}·{{ p.count }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="备注" prop="remark" min-width="140" show-overflow-tooltip />
        </el-table>
      </el-tab-pane>

      <!-- ==================== 部门树 ==================== -->
      <el-tab-pane label="部门树" name="dept">
        <el-table v-loading="loading" :data="depts" row-key="id" default-expand-all
                  :tree-props="{ children: 'children' }">
          <el-table-column label="部门" prop="name" min-width="220" />
          <el-table-column label="编号" align="center" prop="id" width="90" />
          <el-table-column label="层级" align="center" prop="grade" width="80" />
          <el-table-column label="path（禅道写法）" prop="path" width="180" />
          <el-table-column label="负责人" align="center" width="120">
            <template #default="{ row }">{{ row.leaderName || '-' }}</template>
          </el-table-column>
          <el-table-column label="人数" align="center" prop="userCount" width="80" />
          <el-table-column label="排序" align="center" prop="sort" width="80" />
          <el-table-column label="状态" align="center" width="90">
            <template #default="{ row }">
              <el-tag :type="row.status === 0 ? 'success' : 'info'">{{ row.status === 0 ? '开启' : '停用' }}</el-tag>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <!-- ==================== 映射对照 ==================== -->
      <el-tab-pane label="迁移映射对照" name="mapping">
        <el-table :data="mappings">
          <el-table-column label="禅道对象" prop="zentaoTable" width="180" />
          <el-table-column label="yudao 对应" prop="yudaoTable" min-width="220" show-overflow-tooltip />
          <el-table-column label="迁移策略" prop="strategy" min-width="220" show-overflow-tooltip />
          <el-table-column label="字段对照" min-width="320">
            <template #default="{ row }">
              <div v-for="f in row.fieldMapping || []" :key="f" class="text-12px">{{ f }}</div>
              <span v-if="(row.fieldMapping || []).length === 0" class="text-gray-400">-</span>
            </template>
          </el-table-column>
          <el-table-column label="差异与注意" min-width="380">
            <template #default="{ row }">
              <div v-for="n in row.notes || []" :key="n" class="text-12px mb-4px">• {{ n }}</div>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
    </el-tabs>
  </ContentWrap>

  <!-- 用户权限明细 -->
  <Dialog v-model="permDialogVisible" :title="`${currentUser?.realname || ''} 的禅道权限`" width="620">
    <el-table :data="userPermissions" max-height="460" size="small">
      <el-table-column label="模块" prop="module" width="140" />
      <el-table-column label="模块名" prop="moduleName" width="120" />
      <el-table-column label="方法" min-width="260">
        <template #default="{ row }">
          <el-tag v-for="m in row.methods" :key="m" size="small" class="mr-4px mb-4px">{{ m }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="条数" align="center" prop="count" width="70" />
    </el-table>
  </Dialog>
</template>

<script lang="ts" setup>
import * as OrgApi from '@/api/zentao/organization'
import { dateFormatter } from '@/utils/formatTime'

defineOptions({ name: 'ZentaoOrganization' })

const loading = ref(false)
const activeTab = ref('user')
const users = ref<OrgApi.OrgUserVO[]>([])
const roles = ref<OrgApi.OrgRoleVO[]>([])
const depts = ref<OrgApi.OrgDeptVO[]>([])
const mappings = ref<OrgApi.OrgMappingVO[]>([])
const userQuery = reactive<{ keyword?: string; status?: number }>({})

const permDialogVisible = ref(false)
const currentUser = ref<OrgApi.OrgUserVO>()
const userPermissions = ref<OrgApi.OrgPermissionVO[]>([])

const loadUsers = async () => {
  loading.value = true
  try {
    users.value = await OrgApi.getUserList(userQuery)
  } finally {
    loading.value = false
  }
}

const resetUserQuery = () => {
  userQuery.keyword = undefined
  userQuery.status = undefined
  loadUsers()
}

const openUserPermissions = async (row: OrgApi.OrgUserVO) => {
  currentUser.value = row
  userPermissions.value = await OrgApi.getUserPermissions(row.id!)
  permDialogVisible.value = true
}

onMounted(async () => {
  loading.value = true
  try {
    const [userList, roleList, deptTree, mappingList] = await Promise.all([
      OrgApi.getUserList({}),
      OrgApi.getRoleList(true),
      OrgApi.getDeptTree(),
      OrgApi.getMapping()
    ])
    users.value = userList
    roles.value = roleList
    depts.value = deptTree
    mappings.value = mappingList
  } finally {
    loading.value = false
  }
})
</script>
